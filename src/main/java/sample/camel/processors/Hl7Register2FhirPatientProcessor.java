package sample.camel.processors;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.lang3.time.DateParser;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.springframework.stereotype.Component;

import ca.uhn.hl7v2.model.v24.message.ADT_A01;

import ca.uhn.hl7v2.model.v24.segment.PID;

/**
 * A Camel Processor to convert HL7 ADT messages to FHIR Patient resources.
 * <p/>
 * This processor extracts patient information from the HL7 message and creates
 * a FHIR Patient resource.
 * It handles basic fields like patient ID, name, gender, and birth date.
 * <p/>
 * The processor implements the org.apache.camel.Processor interface.
 * It is annotated with @Component to be auto-detected by Spring and used in
 * Camel routes.
 * <p/>
 * See https://hapifhir.io/hapi-fhir/apidocs/hapi-fhir-base/index.html
 * for more information about the FHIR structures.
 * See https://hapifhir.io/hapi-hl7v2/apidocs/index.html
 * for more information about the HL7 structures.
 */
@Component("hl7Register2FhirPatientProcessor")
public class Hl7Register2FhirPatientProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
                System.out.println("Processing HL7 ADT_A01 message to FHIR Patient");
                ADT_A01 msg = exchange.getIn().getBody(ADT_A01.class);
                // Extract the patient information
                final PID pid = msg.getPID();
                String surname = pid.getPatientName()[0].getFamilyName().getFn1_Surname().getValue();
                String name = pid.getPatientName()[0].getGivenName().getValue();
                String patientId = pid.getPatientID().getCx1_ID()
                                .getValue();
                String gender = pid.getAdministrativeSex().getValue().toLowerCase();
                // AdministrativeGender genderEnum = AdministrativeGender.fromCode(gender);
                String birthDateStr = pid.getDateTimeOfBirth().getTimeOfAnEvent().getValue();

                Date birthDate = null;
                try {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                        birthDate = sdf.parse(birthDateStr);
                } catch (ParseException e) {
                        // Manejo del error: log, lanzar excepción, etc.
                        e.printStackTrace();
                }

                // Create a FHIR Patient and set the values
                Patient patient = new Patient();
                System.out.println("Extracted Patient - ID: " + patientId + ", Name: " + name + " " + surname);
                patient.addName().addGiven(name);
                patient.getNameFirstRep().setFamily(surname);
                System.out.println("Setting Patient - ID: " + patientId);
                patient.setId(patientId);
                System.out.println("Setting gender: " + gender);
                patient.setGender(gender.equals("m") ? AdministrativeGender.MALE
                                : gender.equals("f") ? AdministrativeGender.FEMALE
                                                : AdministrativeGender.UNKNOWN);
                System.out.println("Setting birthDate: " + birthDate);
                patient.setBirthDate(birthDate != null ? birthDate : null);
                // Set the patient in the exchange. replace the HL7 message with the FHIR
                // Patient resource
                exchange.getIn().setBody(patient);// Implementation here
        }
}
