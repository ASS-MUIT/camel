package sample.camel.processors;

import org.apache.camel.Exchange;

import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.v24.segment.MSH;
import ca.uhn.hl7v2.parser.PipeParser;
import net.sourceforge.plantuml.utils.Log;

// A simple Processor to verify the HL7 message type and trigger event.
// It extracts the MSH-9 fields and sets them as headers in the Camel exchange. 
// This can be used later in the route to route messages based on their type.
// The fields extracted are:
// MSH-9.1: Message Type (e.g., ADT, ORU)
// MSH-9.2: Trigger Event (e.g., A01, R01)
// And they are inserted as headers:
// HL7MessageType: ADT, ORU, etc.
// HL7TriggerEvent: A01, R01, etc.
@Component("verifyHl7Type")
public class VerifyHl7Type implements Processor {
    @Override
    public void process(Exchange exchange) throws Exception {
        System.out.println("Verifying HL7 message type and trigger event");
        // Get the HL7 message from the exchange body

        Message message = exchange.getIn().getBody(Message.class);
        // Extract the MSH segment
        Segment msh = (Segment) message.get("MSH");
        System.out.println("MSH Segment: " + msh.encode());
        String mshString = msh.encode();
        System.out.println("MSH AS String: " + mshString);
        // Extract MSH-9 fields to get message type and trigger event
        String messageType = msh.getField(9, 0).encode().split("\\^")[0];
        String triggerEvent = msh.getField(9, 0).encode().split("\\^").length > 1
                ? msh.getField(9, 0).encode().split("\\^")[1]
                : "";

        System.out.println("HL7MessageType: " + messageType);
        System.out.println("HL7TriggerEvent: " + triggerEvent);
        // Set them as headers in the exchange
        exchange.getIn().setHeader("HL7MessageType", messageType);
        exchange.getIn().setHeader("HL7TriggerEvent", triggerEvent);

    }
}
