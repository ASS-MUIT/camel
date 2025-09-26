package sample.camel;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.rest.api.MethodOutcome;

@Component
public class OutcomeProcessor implements Processor {
    @Override
    public void process(Exchange exchange) throws Exception {
        MethodOutcome outcome = exchange.getIn().getBody(MethodOutcome.class);
        if (outcome != null && outcome.getId() != null) {
            // Access the ID of the created resource.
            String createdId = outcome.getId().getIdPart();
            exchange.getIn().setBody("Created Patient with ID: " + createdId + " In the FHIR server.");
        } else {
            exchange.getIn()
                    .setBody("Patient created successfully, but the server response was incomplete.");
        }
    }
}
