package com.transs;

import com.sun.jersey.api.client.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Set;

public class Trello implements ALMProvider {

    private final Client client = Client.create();
    private HashMap<String, String> statusIdToNameMapping;
    private HashMap<String, String> statusNameToIdMapping;

    private static final String INITIAL_URL_PREFIX = "https://api.trello.com/1/";
    public static final String KEY = "";
    private String userAccessToken;
    private String userBoardId;

    public Trello(){
    }

    private void createInitialExpectedLists() {
        JSONArray lists = getLists();
        if(statusIdToNameMapping!= null && statusNameToIdMapping != null){
            return;
        }
        statusIdToNameMapping = new HashMap<>(4);
        statusNameToIdMapping = new HashMap<>(4);
        statusIdToNameMapping.put("none", "Not found");

        for (int i = 0; i < lists.length(); i++) {
            fillTheMapping(lists, i);
        }
    }

    private void fillTheMapping(JSONArray lists, int i) {
        System.out.println("Taldo: filling the mapping");
        JSONObject list = lists.getJSONObject(i);
        String id = list.getString("id");
        String name = list.getString("name");
        System.out.println("adding id = " + id + ", name = " + name);
        statusIdToNameMapping.put(id, name.replace(" ", ""));
        statusNameToIdMapping.put(name.replace(" ", ""), id);
        System.out.println("Taldo: finished filling the mapping");
    }

    @Override
    public void updateWorkItems(Set<WorkItemDetails> workItemDetails, String boardId, String token)
    {
        System.out.println("Taldo: updateWorkItems - number of workItems: " + workItemDetails.size());
        userAccessToken = token;
        userBoardId = boardId;
        createInitialExpectedLists();
        for (WorkItemDetails workItemDetail : workItemDetails)
        {
            updateWorkItem(workItemDetail);

        }
    }



    @Override
    public String getWorkItemStatus(String id, String boardId, String token) {
        userAccessToken = token;
        userBoardId = boardId;
        createInitialExpectedLists();
        return statusIdToNameMapping.get(getCard(id).get("idList"));
    }

    private void updateWorkItem(WorkItemDetails workItemDetail)
    {
        try {
            if(workItemDetail.id == null){
                System.out.println("Taldo: id is null");
                return;
            }
            JSONObject card = getCard(workItemDetail.id);
            //String url = INITIAL_URL_PREFIX + "cards/"+ card.getString("id") +"?desc="+ getDescription(card) +"&idList=" + statusNameToIdMapping.get(workItemDetail.newState.replace(" ", "")) + "&" + trelloAuthenticationPostfix();
            String url = INITIAL_URL_PREFIX + "cards/"+ card.getString("id") +"?idList=" + statusNameToIdMapping.get(workItemDetail.newState.replace(" ", "")) + "&" + trelloAuthenticationPostfix();
            System.out.println(url);
            WebResource webResource = client.resource(url);
            ClientResponse response = webResource.put(ClientResponse.class);
            System.out.println("Status = " + response.getStatus());
            String jsonResponse = response.getEntity(String.class);
            System.out.println(jsonResponse);
        }
        catch (JSONException e) {
            e.printStackTrace();
        }
        catch (UniformInterfaceException e) {
            e.printStackTrace();
        }
        catch (ClientHandlerException e) {
            e.printStackTrace();
        }
    }

    private static String getDescription(JSONObject card) {
        String description = card.getString("desc");
        if (!descriptionIsAlreadyUpdatedByTranss(description))
        {
            description += "%0AUpdated%20by%20TRANSS%20-%20Build%20Trust%20Throughout%20Transparency!";
        }
        description = verifyUrlIsNotCorrupted(description);
        return description;
    }

    private static boolean descriptionIsAlreadyUpdatedByTranss(String description)
    {
        return description.endsWith(TranssService.TRANSS_UPDATE_COMMENT);
    }

    private static String verifyUrlIsNotCorrupted(String description)
    {
        description = description.replace(" ", "%20");
        description = description.replace("\n", "%0A");
        return description;
    }



    private JSONArray getLists(){
        System.out.println("Taldo getting the lists");
        String url = INITIAL_URL_PREFIX + "boards/" + userBoardId + "/lists?" + trelloAuthenticationPostfix();
        return httpGetOnUrl(url);
    }

    private JSONArray getCards(){
        String url = INITIAL_URL_PREFIX + "boards/" + userBoardId + "/cards?" + trelloAuthenticationPostfix();
        return httpGetOnUrl(url);
    }

    private JSONArray httpGetOnUrl(String url) {
        System.out.println(url);
        WebResource webResource = client.resource(url);
        ClientResponse response = webResource.get(ClientResponse.class);
        System.out.println("Status = " + response.getStatus());

        String jsonResponse = response.getEntity(String.class);
        System.out.println(jsonResponse);
        JSONArray objects = new JSONArray(jsonResponse);
        System.out.println("Taldo: finised getting the lists");
        return objects;
    }


    private JSONObject getCard(String id){
        System.out.println("Taldo: the card id is: " + id);
        String url = "https://api.trello.com/1/search?query="
        + id.replace(" ", "%20").replace("\n", "%0A") //remove spaces and newlines
        + "&idBoards="+ userBoardId
        + "&modelTypes=cards&boards_limit=10&card_fields=all&cards_limit=10&cards_page=0&card_attachments=false&organizations_limit=10&members_limit=10&"
        + trelloAuthenticationPostfix();


        JSONObject result = getJsonObjectFromURL(client, url);
        String cardsString = result.get("cards").toString();
        JSONArray array = new JSONArray(cardsString);
        if(array.length()>0)
        {
            return array.getJSONObject(0);
        }
        return emptyCard();        
    }

    private static JSONObject getJsonObjectFromURL(Client client, String url) {
        WebResource webResource = client.resource(url);
        ClientResponse response = webResource.get(ClientResponse.class);
        return new JSONObject(response.getEntity(String.class));
    }

    private static JSONObject emptyCard()
    {
        return new JSONObject("{\"idList\":\"none\"}");
    }

    private  String trelloAuthenticationPostfix(){
        return "key=" + KEY +"&token=" + userAccessToken;
    }
}
