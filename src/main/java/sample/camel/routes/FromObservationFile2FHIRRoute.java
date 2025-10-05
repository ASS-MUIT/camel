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
package sample.camel.routes;

import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.v24.message.ORU_R01;
import ca.uhn.hl7v2.model.v24.segment.PID;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.http.ProtocolException;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Component;

/**
 * A simple Camel route that triggers from a file and posts to a FHIR server.
 * <p/>
 * Use <tt>@Component</tt> to make Camel auto detect this route when starting.
 * When this is commented the route is not started.
 * You have the same route codified in yaml in
 * src/main/resources/routes/mycamelrouter.camel.yaml
 * <p/>
 * This route is a slightly modified version of the original in the fhir module
 * of the
 * sample repository https://github.com/apache/camel-spring-boot-examples
 * <p/>
 * It processes HL7 ORU_R01 messages to extract patient information and create
 * FHIR Patient resources, this is not an actual use case in healthcare domain
 * <p/>
 * This route has also been developed as a yaml route in fileinput.camel.yaml,
 * available at resources/routes
 * You have alternatives to see how to receive data from http endpoint in
 * httpinput.camel.yaml and fhirExampleHttpCamel.camel.yaml, in the same folder.
 * <p/>
 */
@Component
// Define the Camel route, by extending RouteBuilder
public class FromObservationFile2FHIRRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        // Step 0: Define message origin and route ID.
        // The route listens for files in the directory defined by the property
        // "input" in application.properties.
        // The routeId is used in the log messages to identify the route.
        // The fhirVersion and serverUrl are also defined in application.properties.

        from("file:{{input}}").routeId("observationfilehl7-fhirserver")
                // Step 1: Handle exceptions related to FHIR server connectivity
                // and HL7 unmarshalling.
                // These are handled in the route using onException blocks.
                // Log the error with details about the server URL and exception message.
                .onException(ProtocolException.class)
                .handled(true)
                .log(LoggingLevel.ERROR,
                        "Error connecting to FHIR server with URL:{{serverUrl}}, please check the application.properties file ${exception.message}")
                .end()
                .onException(HL7Exception.class)
                .handled(true)
                .log(LoggingLevel.ERROR, "Error unmarshalling ${file:name} ${exception.message}")
                .end()
                //
                .log("Converting ${file:name}")
                // Step 2: Unmarshal the HL7 v2 message to a HAPI HL7 message object.
                // The HL7 data format is provided by the camel-hl7 component.
                .unmarshal().hl7()
                // Step 3: Process the HAPI HL7 message to extract patient information
                // and create a FHIR Patient resource.
                // The processor uses HAPI structures to access the HL7 message.
                // See https://hapifhir.io/hapi-hl7v2/apidocs/index.html
                // for more information about the HL7 structures.
                // This Process is implemented as a lambda expression in the place of a
                // org.apache.camel.Processor functional interface.
                // See https://camel.apache.org/manual/processor.html for more information
                // The created Patient resource is set as the message body
                // replacing the original HL7 message.
                // See https://camel.apache.org/components/3.20.x/dataformats/hl7.html
                // for more information about the HL7 data format.
                // See https://hapifhir.io/hapi-fhir/apidocs/hapi-fhir-base/index.html
                // for more information about the FHIR structures.
                // See https://www.hl7.org/fhir/patient.html for more information
                // about the FHIR Patient resource.
                .process(exchange -> {
                    // Read de original HL7 message, type cast to ORU_R01 so this kind of message is
                    // expected
                    // ORU | Observation Result. This message type indicates that the purpose is to
                    // transmit observation data or results.
                    // R01 | Unsolicited Transmission of an Observation Message. This is the trigger
                    // event that indicates a system (e.g., the LIS or RIS) is proactively sending a
                    // newly generated result to a record-keeping system (e.g., the EHR).
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
                // Step 4: Marshal the Patient resource to a JSON string
                // and send it to the FHIR server using the FHIR component.
                // The FHIR component uses the HAPI FHIR structures.
                // See https://camel.apache.org/components/3.20.x/fhir-component.html
                // for more information about the FHIR component.
                // The FHIR server URL is set using the property serverUrl defined
                // in application.properties.
                // The FHIR version is set using the property fhirVersion defined
                // in application.properties.
                .marshal().fhirJson("{{fhirVersion}}")
                // log the patient in order to see the output
                .convertBodyTo(String.class)
                .log("Inserting Patient: ${body}")
                // create Patient in our FHIR server
                .to("fhir://create/resource?inBody=resourceAsString&serverUrl={{serverUrl}}&fhirVersion={{fhirVersion}}")
                // Step 5: Process the MethodOutcome object to safely access the response data.
                // The MethodOutcome object contains information about the result of the
                // create operation, including the ID of the created resource.
                // The processor checks if the ID is present and sets an appropriate message
                // in the exchange.
                // This Process is implemented as a lambda expression in the place of a
                // org.apache.camel.Processor functional interface.
                // See https://camel.apache.org/manual/processor.html for more information
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
