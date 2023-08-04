package com.intuit.developer.helloworld.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 *
 * @author sugiyat
 *
 */

@Controller
@Service
@PropertySource(value="classpath:/application,properties", ignoreResourceNotFound=true)
public class InflowController {
    @Autowired
    private Environment env;

    private static final Logger logger = Logger.getLogger(InflowController.class);
    private static final String failureMsg = "Failed";

    private String hostURL;
    private String companyID;
    private String token;

    @PostConstruct
    public void init() {
        hostURL = env.getProperty("InflowInventoryAPIHost");
        companyID = env.getProperty("InflowCompanyID");
        token = env.getProperty("InflowBearerToken");
    }

    @ResponseBody
    @RequestMapping("/getPurchaseOrders")
    public String getPurchaseOrders() {
        try {
            JsonArray data = getAllPurchaseOrders(new JsonArray(), "");

            String test = "";
            for (JsonElement item : data) {
                JsonObject purchaseOrder = item.getAsJsonObject();
                test += "> Purchase Order Number: " + purchaseOrder.get("orderNumber") + "\n";

                JsonElement vendor = purchaseOrder.get("vendor");
                if (vendor != null) {
                    test += "--- Vendor: " + vendor.getAsJsonObject().get("name") + "\n";
                }

                test += "--- Order Date: " + purchaseOrder.get("orderDate") + "\n";
                test += "--- Request Ship Date: " + purchaseOrder.get("requestShipDate") + "\n";

                JsonElement customFields = purchaseOrder.get("customFields");
                if (customFields != null) {
                    test += "--- Control #: " + customFields.toString() + "\n";
//                    test += "--- Confirmed? (Y/N-Date): " + purchaseOrder.get("customFields").toString() + "\n";
                }


            }

            return test;
        } catch (MalformedURLException e) {
            logger.error("Error while creating URL :: " + e.getMessage());
            return new JSONObject().put("response", failureMsg).toString();
        } catch (IOException e) {
            logger.error("Error while connecting :: " + e.getMessage());
            return new JSONObject().put("response", failureMsg).toString();
        } catch (InterruptedException e) {
            logger.error("Error while sleeping :: " + e.getMessage());
            return new JSONObject().put("response", failureMsg).toString();
        }
    }

    private JsonArray getAllPurchaseOrders(JsonArray data, String afterId) throws MalformedURLException, IOException, InterruptedException {
        LocalDate today = LocalDate.now();
        String fromDate = today.minusMonths(18).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String toDate = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        String urlString = hostURL + companyID + "/purchase-orders" + "?count=100"
                + "&filter[orderDate]={\"fromDate\":\""
                + fromDate + "\",\"toDate\":\""
                + toDate + "\"}";

        if (!afterId.isEmpty())
            urlString += "&after=" + afterId;

        URL url = new URL(urlString);

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json;version=2021-04-26");
        connection.setRequestMethod("GET");

        BufferedReader input = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String output;

        StringBuffer response = new StringBuffer();
        while ((output = input.readLine()) != null)
            response.append(output);
        input.close();

        JsonParser jsonParser = new JsonParser();
        JsonArray page = (JsonArray) jsonParser.parse(response.toString());

        int size = page.size();
        if (size <= 0 && !afterId.isEmpty())
            return data;

        for (JsonElement element : page)
            data.add(element);

        String lastPOId = page.get(size - 1).getAsJsonObject().get("purchaseOrderId").getAsString();

        logger.debug("UPDATE: NEXT PAGE AfterId :: " + afterId + "\n" + urlString);

        Thread.sleep(2000);
        return getAllPurchaseOrders(data, lastPOId);

//        return page;
    }
}
