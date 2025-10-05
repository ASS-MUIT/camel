package sample.camel.processors;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.rest.api.MethodOutcome;

/* This processor handles the response from the FHIR server after creating a resource.
 * It extracts the ID of the created resource from the MethodOutcome object and sets
 * it in the message body for further processing or logging.
 * If the MethodOutcome is null or does not contain an ID, it sets an appropriate
 * message indicating that the creation was successful but the response was incomplete.
 * It also sets the HTTP response code in the message header.
 */
@Component("outcomeProcessor")
public class OutcomeProcessor implements Processor {
    @Override
    public void process(Exchange exchange) throws Exception {
        MethodOutcome outcome = exchange.getIn().getBody(MethodOutcome.class);
        if (outcome != null && outcome.getId() != null) {
            // Access the ID of the created resource.
            String createdId = outcome.getId().getIdPart();
            exchange.getIn().setBody("Created Patient with ID: " + createdId + " In the FHIR server.");
            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, "200");
        } else {
            exchange.getIn()
                    .setBody("Patient created successfully, but the server response was incomplete.");
            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, "504");
        }
    }
}
