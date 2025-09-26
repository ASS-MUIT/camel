package sample.camel;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Component;

import ca.uhn.hl7v2.model.v24.message.ORU_R01;
import ca.uhn.hl7v2.model.v24.segment.PID;

// If you intended to implement a Camel Processor, use org.apache.camel.Processor instead.
// Otherwise, provide a constructor that calls the superclass constructor with required arguments.
@Component
public class hl7ToFhirProcessor implements Processor {

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
