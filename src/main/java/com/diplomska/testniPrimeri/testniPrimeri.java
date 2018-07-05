package com.diplomska.testniPrimeri;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.dstu2.composite.QuantityDt;
import ca.uhn.fhir.model.dstu2.composite.ResourceReferenceDt;
import ca.uhn.fhir.model.dstu2.resource.Bundle;
import ca.uhn.fhir.model.dstu2.resource.Observation;
import ca.uhn.fhir.model.dstu2.resource.Patient;
import ca.uhn.fhir.model.dstu2.valueset.BundleTypeEnum;
import ca.uhn.fhir.model.dstu2.valueset.HTTPVerbEnum;
import ca.uhn.fhir.model.dstu2.valueset.ObservationStatusEnum;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.diplomska.encryptDecrypt.cryptoService;
import com.diplomska.intercept.crypto;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.servlet.ServletContext;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.Random;

public class testniPrimeri {

    public static String HapiRESTfulServer = "http://localhost:8080/hapi/baseDstu2";
    public static String HapiAccessPoint = "http://localhost:7050/hapi.do";
    public static String HapiResourceAccessPoint = "http://localhost:7050/hapi.do/Resource";
    private static cryptoService crypto = new cryptoService();

    public static void main(String[] args) throws IOException, URISyntaxException {
        String given = "Jan";
        String family = "Starc";

        //addPatient(given, family);
        //getPatient("Novi", "Test");
        Patient p = getPatientById(14954);
        System.out.println("Test --> _id: " + p.getId().getIdPartAsLong());
        try{
            addResourceToPatient(p);
        } catch (Exception e){
            e.printStackTrace();
        }

    }

    public static void addResourceToPatient(Patient p) throws IllegalBlockSizeException, BadPaddingException, InvalidKeyException, IOException, NoSuchPaddingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, UnrecoverableEntryException {

        FhirContext ctx = FhirContext.forDstu2();
        String serverBase = HapiRESTfulServer;
        IGenericClient client = ctx.newRestfulGenericClient(serverBase);

        String ident = p.getId().getValue();
        System.out.println("Identifier: " + ident);

        // Create an observation object
        Observation observation = new Observation();
        observation.setStatus(ObservationStatusEnum.FINAL);
        observation
                .getCode()
                .addCoding()
                .setSystem("http://loinc.org")
                .setCode("789-8")
                .setDisplay("Test 2");
        observation.setValue(
                new QuantityDt()
                        .setValue(randomNum())
                        .setUnit("test" + randomNum() + "testEnota")
                        .setSystem("http://unitsofmeasure.org")
                        .setCode("10*12/L"));


        /**
         *  Vse OK, problem je ker se klice iz maina, ki ni servlet in v inicializaciji ne dopusca servlet contexta!
         *
         */
        //observation.setSubject(new ResourceReferenceDt(p.getId().getValue()));
        String _id = String.valueOf(p.getId().getIdPartAsLong());
        System.out.println("ID: " + _id);

        String encryptedRef = "testCeToleDela";
        //ResourceReferenceDt resourceReferenceDt = new ResourceReferenceDt(_id);
        observation.setSubject(new ResourceReferenceDt("Patient/" + encryptedRef));
        //IdDt idDt = new IdDt("hashValue");
        //observation.setSubject(new ResourceReferenceDt("Patient/krneki"));



        Bundle bundle = new Bundle();
        bundle.setType(BundleTypeEnum.TRANSACTION);
        bundle.addEntry()
                .setResource(observation)
                .getRequest()
                .setUrl("Observation")
                .setMethod(HTTPVerbEnum.POST);

        System.out.println(ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle));

        // Create a client and post the transaction to the server
        Bundle resp = client.transaction().withBundle(bundle).execute();

        // Log the response
        System.out.println(ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(resp));
    }

    public static void getPatientByGivenFamily(String given, String family) throws IOException, URISyntaxException {

        HttpClient httpClient = HttpClientBuilder.create().build();
        URIBuilder url = new URIBuilder(HapiAccessPoint);
        url.setParameter("given", given);
        url.setParameter("family", family);
        HttpGet request = new HttpGet(String.valueOf(url));
        HttpResponse response = httpClient.execute(request);
        HttpEntity entity = response.getEntity();
        String responseString = EntityUtils.toString(entity, "UTF-8");
        System.out.println("----- RESPONSE -----\n" + responseString);

    }

    public static Patient getPatientById(int id) throws IOException, URISyntaxException {

        // We're connecting to a DSTU1 compliant server in this example
        FhirContext ctx = FhirContext.forDstu2();
        String serverBase = HapiRESTfulServer;

        IGenericClient client = ctx.newRestfulGenericClient(serverBase);

        // Perform a search
        Bundle results = client
                .search()
                .byUrl(HapiRESTfulServer + "/Patient?_id=" + id)
                .returnBundle(ca.uhn.fhir.model.dstu2.resource.Bundle.class)
                .execute();

        List<Patient> patientList = results.getAllPopulatedChildElementsOfType(Patient.class);
        Patient p;
        if(patientList.size() > 0){
            p = patientList.get(0);
            return p;
        } else {
            System.out.println("No result");
        }

        return null;
    }

    public static void addPatient(String given, String family){
        Patient patient = new Patient();
        // ..populate the patient object..
        patient.addIdentifier().setSystem("urn:system").setValue("17061996");
        patient.addName().addFamily(family).addGiven(given);

        // Log the request
        FhirContext ctx = FhirContext.forDstu2();
        String requestBody = ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(patient);
        System.out.println(requestBody);

        HttpClient httpClient = HttpClientBuilder.create().build();
        try {
            HttpPost request = new HttpPost(HapiAccessPoint);
            StringEntity params = new StringEntity(requestBody);
            request.addHeader("content-type", "application/json");
            request.setEntity(params);
            HttpResponse response = httpClient.execute(request);
            HttpEntity entity = response.getEntity();
            String responseString = EntityUtils.toString(entity, "UTF-8");
            System.out.println("----- RESPONSE -----\n" + responseString);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static int randomNum(){
        Random r = new Random();
        int Low = 10;
        int High = 100;
        int Result = r.nextInt(High-Low) + Low;
        return Result;
    }
}
