package sample.camel.processors;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.hl7.fhir.r4.model.Patient;
import ca.uhn.hl7v2.model.v24.message.ORU_R01;
import ca.uhn.hl7v2.model.v24.segment.PID;
import org.springframework.stereotype.Component;

/*
 * A Camel Processor to convert HL7 ORU_R01 messages to FHIR Patient resources.  
 * These messages are typically used for observations and results,
 * but for this example we extract patient information to create a FHIR Patient resource.
 * <p/>
 * The processor implements the org.apache.camel.Processor interface.
 * It is annotated with @Component to be auto-detected by Spring and used in Camel routes.
 * <p/>
 * See https://hapifhir.io/hapi-fhir/apidocs/hapi-fhir-base/index.html
 * for more information about the FHIR structures.
 * See https://hapifhir.io/hapi-hl7v2/apidocs/index.html
 * for more information about the HL7 structures.
 * This is not a typical use case, as ORU_R01 messages usually contain observation data,
 * but it serves as an example of processing HL7 messages and creating FHIR resources.
 */
@Component("hl7ToFhirProcessor")

public class Hl7ToFhirProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
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
                exchange.getIn().setBody(patient);// Implementation here
        }
}
