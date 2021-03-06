package com.diplomska.hapi;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.ExtensionDt;
import ca.uhn.fhir.model.dstu2.resource.Bundle;
import ca.uhn.fhir.model.dstu2.resource.Condition;
import ca.uhn.fhir.model.dstu2.resource.Observation;
import ca.uhn.fhir.model.primitive.StringDt;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.StringClientParam;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.util.List;

import static com.diplomska.constants.address.HapiCryptoCondition;
import static com.diplomska.constants.address.HapiCryptoObservation;
import static com.diplomska.constants.address.HapiRESTfulServer;

@WebServlet(urlPatterns = {"/hapi.do/Condition"})
public class hapiCondition extends HttpServlet {

        HttpClient httpClient = HttpClientBuilder.create().build();
        URIBuilder uri;

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

            System.out.println("HERE conditon for patient : " + request.getParameter("patient"));
            // Find all Observation for Patient with _id
            String _id = request.getParameter("patient");

            try {
            // Send request to crypto --> Encrypt _id
            uri = new URIBuilder(HapiCryptoCondition);
            uri.setParameter("encrypt", "true");
            uri.setParameter("_id", _id);
            HttpGet requestToCrypto = new HttpGet(String.valueOf(uri));
            HttpResponse encryptedGet = httpClient.execute(requestToCrypto);

            // Crypto returns JSON object with encrypted search parameters
            String encryptedJson = EntityUtils.toString(encryptedGet.getEntity());
            JsonObject jObj = new Gson().fromJson(encryptedJson, JsonObject.class);
            String _idEnc = jObj.get("_id").getAsString();

            // Create FHIR context
            FhirContext ctx = FhirContext.forDstu2();
            IGenericClient client = ctx.newRestfulGenericClient(HapiRESTfulServer);

            // Search with encoded parameters
            Bundle search = client
                    .search()
                    .forResource(Condition.class)
                    .where(new StringClientParam("_content").matches().value("Patient/" + _idEnc))
                    .returnBundle(Bundle.class)
                    .encodedJson()
                    .execute();

            // Convert bundle to List<Observation>
            List<Condition> resultArray = search.getAllPopulatedChildElementsOfType(Condition.class);
            System.out.println("Result Array size: " + resultArray.size());


            // Loop through the Observation list, decrypt hashed parameters
            for (Condition c : resultArray) {

                // Decrypt the values
                try {
                    // Find the right extension, decrypt the value
                    List<ExtensionDt> extList = c.getUndeclaredExtensions();
                    if(extList.size() > 0){
                        for(ExtensionDt ext : extList){
                            if(ext.getElementSpecificId().equals("encryptedReference")){
                                ext.setValue(new StringDt("Patient/" + _id));
                            }
                        }
                    }

                    // Write log
                    System.out.println("Found " + search.getEntry().size() + " results.");
                    System.out.println(ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(search));

                } catch (Exception e){
                    e.printStackTrace();
                }
            }

            // Send response
            PrintWriter out = response.getWriter();
            out.println(ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(search));

        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    // Ko dobimo POST request - nalaganje resourca na bazo
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

        // Encrypt the resource
        HttpClient httpClient = HttpClientBuilder.create().build();
        HttpPost requestToCrypto = new HttpPost(HapiCryptoCondition);
        String requestBody = IOUtils.toString(new InputStreamReader(request.getInputStream()));
        requestToCrypto.setEntity(new StringEntity(requestBody));

        // Get the response, generate a usable form out of it
        HttpResponse encryptedResource = httpClient.execute(requestToCrypto);

        // Send the encrypted response to HAPI endpoint
        HttpPost encryptedToHapi = new HttpPost(HapiRESTfulServer);
        encryptedToHapi.setEntity(encryptedResource.getEntity());
        encryptedToHapi.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        HttpResponse responseFromHapi = httpClient.execute(encryptedToHapi);
        String responseFromHapiString = EntityUtils.toString(responseFromHapi.getEntity());
        System.out.println("Response from HAPI: " + responseFromHapiString);
        PrintWriter out = response.getWriter();
        out.println(responseFromHapiString);
    }
}