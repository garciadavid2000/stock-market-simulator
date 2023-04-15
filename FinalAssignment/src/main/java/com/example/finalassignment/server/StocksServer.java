package com.example.finalassignment.server;

import com.example.finalassignment.service.StocksResource;
import com.example.finalassignment.util.Profile;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

import static com.example.finalassignment.service.StocksResource.writeJsonStocks;
import static com.example.finalassignment.service.StocksResource.writeJsonGlobal;
import static com.example.finalassignment.service.StocksResource.jsonServer;
import static com.example.finalassignment.service.StocksResource.writeFile;

/**
 * This class represents a web socket server, a new connection is created
 * **/
@ServerEndpoint(value="/ws/stocks")
public class StocksServer {

    //users stores the userId and matches it to their profile class to store data
    private HashMap<String, Profile> users = new HashMap<>();
    //currentPrices stores the current prices for all stocks in the json for easy access
    private HashMap<String, Double> currentPrices = new HashMap<>();
    //globalSharesHeld stores how many shares are held for all stocks currently
    private HashMap<String, Integer> globalSharesHeld = new HashMap<>();

    @OnOpen
    public void open(Session session) throws IOException, EncodeException {
        System.out.println("HI");
        //initialize our prices locally using the json file for current prices
        currentPrices = pullCurrentPrices();
        //user variables
        String userId = session.getId();
        Profile profile = new Profile(userId);

        //storing user data locally
        users.put(userId, profile);
    }

    @OnClose
    public void close(Session session) throws IOException, EncodeException {
        System.out.println("BYE");
        //user variable
        String userId = session.getId();

        //cleanup when a user disconnects
        if (users.containsKey(userId)) {
            users.remove(userId);
        }
    }

    @OnMessage
    public void handleMessage(String tradeQuants, Session session) throws IOException, EncodeException {
        //useful variables
        String userId = session.getId();
        Profile profile = users.get(userId);
        double balance = profile.getBalance();
        HashMap<String, Integer> requestedTrades = new HashMap<>();

        JSONObject quants = new JSONObject(tradeQuants);
        //to filter request types
        String type = quants.get("type").toString();

        if(type.equals("balance")) {
            session.getBasicRemote().sendText("{\"mess\":5}");
            return;
        }

        //this happens on each tick
        if (type.equals("update")) {
            //send new data back to frontend to be displayed
            returnInfo(session);

            //this condition makes sure that the stocks only update once per 'tick', not once per active user
            //only 1 user will pass this condition each time, so it will only be called once
            Object[] a = users.keySet().toArray();
            Arrays.sort(a);
            if (userId.equals(a[0])) {
                updatePrices();
            }
            return;
        }


        JSONArray quantsArray = quants.getJSONArray("quantities");
        //loop through json array
        for (int i = 0; i < quantsArray.length(); i++) {
            JSONObject stock = quantsArray.getJSONObject(i);
            String stockSymbol = stock.getString("symbol");
            String quantity = stock.getString("quantity");
            Integer intQuantity = Integer.parseInt(quantity);

            requestedTrades.put(stockSymbol, intQuantity);
        }

        boolean valid = verifyRequest(userId, requestedTrades);

        if (valid) { //only if user has the resources to make the transaction
            updateProfileShares(profile, requestedTrades);
            updateGlobalShares(requestedTrades);

            //write updated values into global shares json
            StocksResource.writeJsonGlobal(globalSharesHeld);
        }
    }

    //storing updated number of stocks and balance in profile objects
    public void updateProfileShares(Profile profile, HashMap<String, Integer> trades) {
        double cost = 0;
        for(String key: trades.keySet()) {
            //cost times how many purchased or sold
            cost += trades.get(key)*currentPrices.get(key);
            //if statement for safety, user might not have this stock in hashmap
            if (profile.stockProfile.containsKey(key)) {
                profile.stockProfile.put(key, profile.stockProfile.get(key)+trades.get(key));
            } else {
                profile.stockProfile.put(key, trades.get(key));
            }
        }
        profile.setBalance(profile.getBalance()+cost);
    }

    //adds or subtracts purchased/sold shares from the global count between users
    public void updateGlobalShares(HashMap<String, Integer> trades) {
        for(String key: trades.keySet()) {
            globalSharesHeld.put(key, globalSharesHeld.get(key)+trades.get(key));
        }
    }

    //pull prices from stocks.json, and storing them locally
    public HashMap<String, Double> pullCurrentPrices() throws IOException {
        HashMap<String, Double> currentPrices = new HashMap<>();

        //iterating through passed json object
        JSONObject json = jsonServer("stocks.json");
        JSONArray stocks = json.getJSONArray("stocks");

        for (int i = 0; i < stocks.length(); i++) {
            JSONObject stock = stocks.getJSONObject(i);
            String stockSymbol = stock.getString("symbol");
            String price = stock.getString("price");
            Double doublePrice = Double.parseDouble(price);

            //put will replace current values
            currentPrices.put(stockSymbol, doublePrice);
        }

        return currentPrices;
    }

    public boolean verifyRequest(String userId, HashMap<String, Integer> requestedTrades) {
        //useful variables
        Profile profile = users.get(userId);
        double balance = profile.getBalance();
        double sum = 0;

        //iterating through all trades
        for(String key: requestedTrades.keySet()) {
            if (requestedTrades.get(key) < 0) {
                if (profile.stockProfile.get(key) + requestedTrades.get(key) < 0) {
                    //immediately end if we do not have enough stocks to sell desired amount
                    return false;
                }
            } else {
                sum += requestedTrades.get(key)*(currentPrices.get(key));
            }
        }
        //whether we have enough money to purchase these stocks
        return balance>sum;
    }

    public void updatePrices() throws IOException {
        //updates the stocks randomly
        for (String key : currentPrices.keySet()) {
            Random rand = new Random();
            // Obtain a number [-1, 1]
            double min = 0.0;
            double max = 2.0;
            double n = min + (max - min) * rand.nextDouble();
            // increase by somewhere between [-5, 5]
            currentPrices.put(key, currentPrices.get(key)+(5*n));
        }
        //write these values to the json file for stock prices
        writeJsonStocks(currentPrices);
    }

    public void returnInfo(Session session) throws IOException {
        //useful variables
        String userId = session.getId();
        Profile profile = users.get(userId);
        String message = "";

        //creating stringified json
        message += "{\n\t\"stocks\": [\n";
        int count = 0;
        for (String key : profile.stockProfile.keySet()) {
            message += "\t\t{\n";
            message += "\t\t\t\"symbol\":\"" + key + "\",\n";
            message += "\t\t\t\"held\":" + profile.stockProfile.get(key) + ",\n";
            message += "\t\t}";
            if (count < profile.stockProfile.keySet().size()-1) {
                message += ",";
            }
            message += "\n";
            count += 1;
        }
        message += "\t],\n";
        message += "\t\"balance\":" + profile.getBalance() + "\n}";

        session.getBasicRemote().sendText(message);
        //return stringified json with users stock profile and balance
    }
}
