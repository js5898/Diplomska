package com.diplomska.intercept;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.dstu2.composite.ResourceReferenceDt;
import ca.uhn.fhir.model.dstu2.resource.BaseResource;
import ca.uhn.fhir.model.dstu2.resource.Bundle;
import ca.uhn.fhir.model.dstu2.resource.Observation;
import ca.uhn.fhir.model.dstu2.resource.Patient;
import ca.uhn.fhir.model.primitive.StringDt;
import ca.uhn.fhir.rest.client.api.IGenericClient;
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
import java.util.ArrayList;
import java.util.List;

import static com.diplomska.constants.address.HapiCryptoObservation;
import static com.diplomska.constants.address.HapiRESTfulServer;

@WebServlet(urlPatterns = {"/hapi.do/Observation"})
public class hapiObservation extends HttpServlet {

    // Get requesti - iskanje pacientov
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        // Get the parameter
        String _id = request.getParameter("_id");
        System.out.println("All resources for: " + _id);

        HttpClient httpClient = HttpClientBuilder.create().build();
        URIBuilder uri;
        try {
            // Send request to crypto --> Encrypt search parameters
            uri = new URIBuilder(HapiCryptoObservation);
            uri.setParameter("encrypt", "true");
            uri.setParameter("_id", _id);
            HttpGet requestToCrypto = new HttpGet(String.valueOf(uri));
            HttpResponse encryptedGet = httpClient.execute(requestToCrypto);

            // Crypto returns JSON object with encrypted search parameters
            String encryptedJson = EntityUtils.toString(encryptedGet.getEntity());
            JsonObject jObj = new Gson().fromJson(encryptedJson, JsonObject.class);
            String _idEnc = jObj.get("_id").getAsString();
            System.out.println("_id enc: " + _idEnc);

            // Search with encoded parameters
            FhirContext ctx = FhirContext.forDstu2();
            IGenericClient client = ctx.newRestfulGenericClient(HapiRESTfulServer);


            // Search for the Patient - hashed value
            Bundle search = client
                    .search()
                    .forResource(Observation.class)
                    .where(Observation.PATIENT.hasId("14954"))
                    .returnBundle(ca.uhn.fhir.model.dstu2.resource.Bundle.class)
                    .encodedJson()
                    .execute();

            // Convert bundle to List<Observation>
            List<Observation> resultArray = search.getAllPopulatedChildElementsOfType(Observation.class);
            System.out.println("Result Array size: " + resultArray.size());

            // Loop through the patient list, decrypt hashed parameters
            for (Observation o : resultArray) {
                String idPartEncrypted = String.valueOf(o.getSubject().getReference().getIdPartAsLong());
                //String idPartEncrypted = idEncrypted.substring(idEncrypted.lastIndexOf("/") + 1);
                System.out.println("Line 87 - Sent to decrypt: " + idPartEncrypted);

                try {
                    // Decrypt the values
                    uri = new URIBuilder(HapiCryptoObservation);
                    uri.setParameter("encrypt", "false");
                    uri.setParameter("_id", idPartEncrypted);
                    HttpGet requestToCrypto2 = new HttpGet(String.valueOf(uri));
                    HttpResponse encryptedGet2 = httpClient.execute(requestToCrypto2);

                    // Crypto returns JSON object with encrypted search parameters
                    String encryptedJson2 = EntityUtils.toString(encryptedGet2.getEntity());
                    //System.out.println("Line before error:\n" + encryptedJson2);
                    JsonObject jObj2 = new Gson().fromJson(encryptedJson2, JsonObject.class);
                    String idDecrypt = jObj2.get("_id").getAsString();
                    System.out.println("ID_Decrypt (is ok?): " + idDecrypt);

                    // Handle the resource conversion and change the value of object p
                    o.setSubject(new ResourceReferenceDt("Observation/" + idDecrypt));

                    // Write log
                    System.out.println("Found " + search.getEntry().size() + " results.");
                    String result = ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(search);
                    //System.out.println("RESULT: " + result);

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
        HttpPost requestToCrypto = new HttpPost(HapiCryptoObservation);
        String requestBody = IOUtils.toString(new InputStreamReader(request.getInputStream()));
        System.out.println("Req - HAPI" + requestBody);
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