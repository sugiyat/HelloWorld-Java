package com.intuit.developer.helloworld.controller;

import org.apache.log4j.Logger;
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
            URL url = new URL(hostURL + companyID + "/purchase-orders");
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

            return response.toString();
        } catch (MalformedURLException e) {
            logger.error("Error while creating URL :: " + e.getMessage());
            return new JSONObject().put("response", failureMsg).toString();
        } catch (IOException e) {
            logger.error("Error while connecting :: " + e.getMessage());
            return new JSONObject().put("response", failureMsg).toString();
        }
    }
}
