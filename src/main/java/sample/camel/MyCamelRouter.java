/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package sample.camel;

import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.v24.message.ORU_R01;
import ca.uhn.hl7v2.model.v24.segment.PID;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.http.ProtocolException;
import org.hl7.fhir.r4.model.Patient;

/**
 * A simple Camel route that triggers from a file and posts to a FHIR server.
 * <p/>
 * Use <tt>@Component</tt> to make Camel auto detect this route when starting.
 * When this is commented the route is not started.
 * You have the same route codified in yaml in
 * src/main/resources/routes/mycamelrouter.camel.yaml
 * <p/>
 */
// @Component
public class MyCamelRouter extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("file:{{input}}").routeId("fhir-example")
                .onException(ProtocolException.class)
                .handled(true)
                .log(LoggingLevel.ERROR,
                        "Error connecting to FHIR server with URL:{{serverUrl}}, please check the application.properties file ${exception.message}")
                .end()
                .onException(HL7Exception.class)
                .handled(true)
                .log(LoggingLevel.ERROR, "Error unmarshalling ${file:name} ${exception.message}")
                .end()
                .log("Converting ${file:name}")
                // unmarshall file to hl7 message
                .unmarshal().hl7()
                // very simple mapping from a HLV2 patient to dstu3 patient
                .process(exchange -> {
                    // Read de original HL7 message
                    ORU_R01 msg = exchange.getIn().getBody(ORU_R01.class);
                    // Extract the patient information
                    final PID pid = msg.getPATIENT_RESULT().getPATIENT().getPID();
                    String surname = pid.getPatientName()[0].getFamilyName().getFn1_Surname().getValue();
                    String name = pid.getPatientName()[0].getGivenName().getValue();
                    String patientId = msg.getPATIENT_RESULT().getPATIENT().getPID().getPatientID().getCx1_ID()
                            .getValue();
                    // Create a FHIR Patient and set the values
                    Patient patient = new Patient();
                    patient.addName().addGiven(name);
                    patient.getNameFirstRep().setFamily(surname);
                    patient.setId(patientId);
                    // Set the patient in the exchange. replace the HL7 message with the FHIR
                    // Patient resource
                    exchange.getIn().setBody(patient);
                })
                // marshall to JSON for logging
                .marshal().fhirJson("{{fhirVersion}}")
                // log the patient in order to see the output
                .convertBodyTo(String.class)
                .log("Inserting Patient: ${body}")
                // create Patient in our FHIR server
                .to("fhir://create/resource?inBody=resourceAsString&serverUrl={{serverUrl}}&fhirVersion={{fhirVersion}}")
                // Step 5: Process the MethodOutcome object to safely access the response data.
                .process(exchange -> {
                    MethodOutcome outcome = exchange.getIn().getBody(MethodOutcome.class);
                    if (outcome != null && outcome.getId() != null) {
                        // Access the ID of the created resource.
                        String createdId = outcome.getId().getIdPart();
                        exchange.getIn().setBody("Created Patient with ID: " + createdId + " In the FHIR server.");
                    } else {
                        exchange.getIn()
                                .setBody("Patient created successfully, but the server response was incomplete.");
                    }
                })

                .log("Fhir Server ${body}");

    }

}
