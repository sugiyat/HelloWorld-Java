package com.intuit.developer.helloworld.controller;

import java.util.List;

import javax.servlet.http.HttpSession;

import com.intuit.ipp.core.IEntity;
import com.intuit.ipp.data.Bill;
import com.intuit.ipp.data.Line;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intuit.developer.helloworld.client.OAuth2PlatformClientFactory;
import com.intuit.developer.helloworld.helper.QBOServiceHelper;
import com.intuit.ipp.data.CompanyInfo;
import com.intuit.ipp.data.Error;
import com.intuit.ipp.exception.FMSException;
import com.intuit.ipp.exception.InvalidTokenException;
import com.intuit.ipp.services.DataService;
import com.intuit.ipp.services.QueryResult;
import com.intuit.oauth2.client.OAuth2PlatformClient;
import com.intuit.oauth2.data.BearerTokenResponse;
import com.intuit.oauth2.exception.OAuthException;

/**
 * @author dderose, sugiyat
 *
 */
@Controller
public class QBOController {
	
	@Autowired
	OAuth2PlatformClientFactory factory;
	
	@Autowired
    public QBOServiceHelper helper;

	
	private static final Logger logger = Logger.getLogger(QBOController.class);
	private static final String failureMsg="Failed";

	@ResponseBody
	@RequestMapping("/getBills")
	public String callQBOBills(HttpSession session) {
		String realmId = (String) session.getAttribute("realmId");
		if (StringUtils.isEmpty(realmId))
			return new JSONObject().put("response","No realm ID.  QBO calls only work if the accounting scope was passed!").toString();

		String accessToken = (String) session.getAttribute("access_token");

		try {
			// get DataService
			DataService service = helper.getDataService(realmId, accessToken);

			// get all the bills
			String sql = "select * from bill";
			QueryResult queryResult = service.executeQuery(sql);
			logger.info(queryResult.toString());
			return processGetBills(failureMsg, queryResult);
		} catch (InvalidTokenException e) {
			/*
			 * Handle 401 status code -
			 * If a 401 response is received, refresh tokens should be used to get a new access token,
			 * and the API call should be tried again.
			 */
			logger.error("Error while calling executeQuery :: " + e.getMessage());

			// refresh tokens
			logger.info("received 401 during bill call, refreshing tokens now");
			OAuth2PlatformClient client  = factory.getOAuth2PlatformClient();
			String refreshToken = (String)session.getAttribute("refresh_token");

			try {
				BearerTokenResponse bearerTokenResponse = client.refreshToken(refreshToken);
				session.setAttribute("access_token", bearerTokenResponse.getAccessToken());
				session.setAttribute("refresh_token", bearerTokenResponse.getRefreshToken());

				// use new tokens to get all bills
				logger.info("calling bill using new tokens");
				DataService service = helper.getDataService(realmId, accessToken);

				String sql = "select * from bill";
				QueryResult queryResult = service.executeQuery(sql);
				return processGetBills(failureMsg, queryResult);
			} catch (OAuthException e1) {
				logger.error("Error while calling bearer token :: " + e.getMessage());
				return new JSONObject().put("response", failureMsg).toString();
			} catch (FMSException e1) {
				logger.error("Error while calling bill currency :: " + e.getMessage());
				return new JSONObject().put("response", failureMsg).toString();
			}
		} catch (FMSException e) {
			List<Error> list = e.getErrorList();
			list.forEach(error -> logger.error("Error while calling executeQuery :: " + error.getMessage()));
			return new JSONObject().put("response", failureMsg).toString();
		}
	}

	// TODO: Create a bill type to parse and manage data
	private String processGetBills(String failureMsg, QueryResult queryResult) {
		String billData = "";
		for (Bill bill : (List<Bill>) queryResult.getEntities()) {
			billData += "> " + bill.getDocNumber() + "\n";
			billData += "--- Vendor: " + bill.getVendorRef().getName() + "\n";
			billData += "--- Due Date: " + bill.getDueDate() + "\n";
			billData += "--- Bill Date: " + bill.getTxnDate() + "\n";
			billData += "--- Total Amount: " + bill.getTotalAmt() + "\n";
			billData += "--- Details: \n";
			for (Line line : bill.getLine()) {
				billData += "---> Description: " + line.getDescription() + "\n";
				billData += "------ Amount: " + line.getAmount() + "\n";
			}
			billData += "\n";
		}
		return billData;
	}

}
