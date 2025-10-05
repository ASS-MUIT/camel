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

import ca.uhn.hl7v2.HL7Exception;
import sample.camel.processors.Hl7Register2FhirPatientProcessor;
import sample.camel.processors.OutcomeProcessor;
import sample.camel.processors.VerifyHl7Type;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.http.ProtocolException;
import org.springframework.stereotype.Component;

/**
 * A simple Camel route that triggers from a file and posts to a FHIR server.
 * <p/>
 * Use <tt>@Component</tt> to make Camel auto detect this route when starting.
 * When this is commented the route is not started.
 * You have the same route codified in yaml in
 * src/main/resources/routes/mycamelrouter.camel.yaml
 * <p/>
 */
@Component
// Define the Camel route, by extending RouteBuilder
public class FromRegisterPut2FHIRRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        // Step 0: Define message origin and route ID.
        // The route listens for files in the directory defined by the property
        // "input" in application.properties.
        // The routeId is used in the log messages to identify the route.
        // The fhirVersion and serverUrl are also defined in application.properties.
        rest("/hl7receiver")
                .put()
                .consumes("text/plain")
                .produces("application/json")
                .to("direct:hl7");

        // ==============================================================================
        // 2. Ruta Principal de Procesamiento (Anteriormente 'file:', ahora
        // 'direct:hl7')
        // Contiene la lógica de transformación HL7 a FHIR y enrutamiento condicional.
        // ==============================================================================
        from("direct:hl7").routeId("putregisterhl7-fhirserver")
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

                .convertBodyTo(String.class)
                .process(exchange -> {
                    String raw = exchange.getIn().getBody(String.class);
                    // Normalizar saltos de línea a \r
                    raw = raw.replaceAll("\\r?\\n", "\r");
                    // Desescapar HTML si es necesario
                    raw = org.apache.commons.text.StringEscapeUtils.unescapeHtml4(raw);
                    exchange.getIn().setBody(raw);
                })
                .unmarshal().hl7()
                .log("HL7 Message after unmarshal: ${body}")
                .process(new VerifyHl7Type())

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

                .convertBodyTo(String.class)
                .process(exchange -> {
                    String raw = exchange.getIn().getBody(String.class);
                    // Normalizar saltos de línea a \r
                    raw = raw.replaceAll("\\r?\\n", "\r");
                    // Desescapar HTML si es necesario
                    raw = org.apache.commons.text.StringEscapeUtils.unescapeHtml4(raw);
                    exchange.getIn().setBody(raw);
                })
                .unmarshal().hl7()
                .log("HL7 Message after unmarshal: ${body}")
                .process(new VerifyHl7Type())
                // Step 2: Unmarshal the HL7 v2 message to a HAPI HL7 message object.
                // The HL7 data format is provided by the camel-hl7 component.

                // Verify the HL7 message type and trigger event
                // and set them as headers in the Camel exchange.
                // This is done in a separate Processor class VerifyHl7Type
                // to keep the route clean and modular.
                // The Processor extracts the MSH-9 fields and sets them as headers:
                // HL7MessageType: ADT, ORU, etc.
                // HL7TriggerEvent: A01, R01, etc.
                // See the VerifyHl7Type class for more details.
                // This Processor is annotated with @Component so it is auto detected by
                // Spring and Camel.
                // See https://camel.apache.org/manual/processor.html for more information
                // See https://camel.apache.org/components/3.20.x/dataformats/hl7.html
                // for more information about the HL7 data format.
                // See https://hapifhir.io/hapi-hl7v2/apidocs/index.html
                // for more information about the HL7 structures.

                .process(new VerifyHl7Type())
                // The route expects ADT^A04 messages for patient registration.
                // ADT | Admit Discharge Transfer. This message type indicates that the purpose
                // is to
                // transmit patient admission, discharge, or transfer information.
                // We are interested in A04 | Register a Patient. This trigger event indicates
                // that a patient is being
                // registered in a healthcare system, typically for outpatient services.

                .choice()
                .when(exchange -> {
                    String type = exchange.getIn().getHeader("HL7MessageType", String.class);
                    String event = exchange.getIn().getHeader("HL7TriggerEvent", String.class);
                    return !"ADT".equals(type) || !"A04".equals(event); // condición de error
                })
                .log(LoggingLevel.WARN, "Received unsupported HL7 message: ${header.CamelFileName}")
                .setBody().constant("ERROR: Unexpected type message. Expected ADT^A04.")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
                .otherwise()
                .log("Valid ADT^A04 message. Processing...")

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
                .process(new Hl7Register2FhirPatientProcessor())
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
                .process(new OutcomeProcessor())
                .log("Fhir Server ${body}")
                .endChoice();

    }

}
