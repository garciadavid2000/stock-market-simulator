package com.example.finalassignment;

import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.example.finalassignment.StocksResource.jsonServer;

/**
 * This class represents a web socket server, a new connection is created
 * **/
@ServerEndpoint(value="/ws/stocks")
public class StocksServer {


    //users stores the userId and matches it to their profile class to store data
    private Map<String, Profile> users = new HashMap<>();
    //currentPrices stores the current prices for all stocks in the json for easy access
    private Map<String, Double> currentPrices = pullCurrentPrices();
    //globalSharesHeld stores how many shares are held for all stocks currently
    private Map<String, Integer> globalSharesHeld = new HashMap<>();

    @OnOpen
    public void open(Session session) throws IOException, EncodeException {
        RemoteEndpoint.Basic out = session.getBasicRemote();
        String userId = session.getId();
        Profile profile = new Profile(userId);

        users.put(userId, profile);

        //out.sendText(createMessage("Server "+roomID, "Welcome to the server. Please enter a username."));
    }

    @OnClose
    public void close(Session session) throws IOException, EncodeException {
        //useful variables
        String userId = session.getId();

        if (users.containsKey(userId)) {
            users.remove(userId);
        }
    }

    @OnMessage
    public JSONObject handleMessage(String tradeQuants, Session session) throws IOException, EncodeException {
        //useful variables
        String userId = session.getId();
        Profile profile = users.get(userId);
        HashMap<String, Integer> requestedTrades = new HashMap<>();

        JSONObject quants = new JSONObject(tradeQuants);
        JSONArray quantsArray = quants.getJSONArray("quantities");

        for (int i = 0; i < quantsArray.length(); i++) {
            JSONObject stock = quantsArray.getJSONObject(i);
            String stockSymbol = stock.getString("symbol");
            String quantity = stock.getString("quantity");
            Integer intQuantity = Integer.parseInt(quantity);

            requestedTrades.put(stockSymbol, intQuantity);
        }

        boolean valid = verifyRequest(userId, requestedTrades);

        if (valid) {
            updateProfileShares(profile, requestedTrades);
            updateGlobalShares(requestedTrades);

            return null; //change this to data for front end
        }

        return null; //if info is not valid
    }

    public void updateProfileShares(Profile profile, HashMap<String, Integer> trades) {
        for(String key: trades.keySet()) {
            profile.stockProfile.put(key, globalSharesHeld.get(key)+trades.get(key));
        }
    }

    public void updateGlobalShares(HashMap<String, Integer> trades) {
        for(String key: trades.keySet()) {
            globalSharesHeld.put(key, globalSharesHeld.get(key)+trades.get(key));
        }
    }

    public HashMap<String, Double> pullCurrentPrices() {
        HashMap<String, Double> currentPrices = new HashMap<>();

        JSONObject json = jsonServer();
        JSONArray stocks = json.getJSONArray("stocks");

        for (int i = 0; i < stocks.length(); i++) {
            JSONObject stock = stocks.getJSONObject(i);
            String stockSymbol = stock.getString("symbol");
            String price = stock.getString("price");
            Double doublePrice = Double.parseDouble(price);

            currentPrices.put(stockSymbol, doublePrice);
        }
        return currentPrices;
    }

    public boolean verifyRequest(String userId, HashMap<String, Integer> requestedTrades) {

        Profile profile = users.get(userId);
        double balance = profile.getBalance();
        double sum = 0;

        for(String key: requestedTrades.keySet()) {
            if (requestedTrades.get(key) < 0) {
                if (profile.stockProfile.get(key) + requestedTrades.get(key) < 0) {
                    return false;
                }
            } else {
                sum += requestedTrades.get(key)*(currentPrices.get(key));
            }
        }

        return balance>sum;
    }
}