package com.medblocks.plugins;

import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.medblocks.openfhir.plugin.api.FormatConverter;
import com.google.gson.JsonElement;
import org.hl7.fhir.r4.model.Ratio;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.hapi.fluentpath.FhirPathR4;
import ca.uhn.fhir.context.FhirContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestPlugin extends Plugin {

    private static final Logger log = LoggerFactory.getLogger(TestPlugin.class);

    public TestPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void start() {
        log.info("Hello world! TestPlugin is starting...");
        log.info("TestPlugin.start()");
    }

    @Override
    public void stop() {
        log.info("TestPlugin.stop()");
    }

    /**
     * Extension implementation for converting between FHIR and OpenEHR formats
     */
    @Extension
    public static class TestFormatConverter implements FormatConverter {
        
        private static final Logger log = LoggerFactory.getLogger(TestFormatConverter.class);
        

        @Override
        public boolean applyFhirToOpenEhrMapping(String mappingCode, String openEhrPath, Object fhirValue, 
                                               String openEhrType, Object flatComposition) {
            log.info("Applying FHIR to OpenEHR mapping function: {}", mappingCode);
            log.info("OpenEHR Path: {}, Value type: {}, OpenEHR Type: {}", openEhrPath, 
                     fhirValue != null ? fhirValue.getClass().getName() : "null", openEhrType);
            
            // Dispatch to the appropriate mapping function based on the mappingCode
            switch (mappingCode) {
                case "dosageDurationToAdministrationDuration":
                    return dosageDurationToAdministrationDuration(openEhrPath, fhirValue, openEhrType, flatComposition);
                case "ratio_to_dv_quantity":
                    return ratio_to_dv_quantity(openEhrPath, fhirValue, openEhrType, flatComposition);
                // Add other mapping functions as needed
                default:
                    log.warn("Unknown mapping code: {}", mappingCode);
                    return false;
            }
        }
        
        @Override
        public Object applyOpenEhrToFhirMapping(String mappingCode, String openEhrPath, 
                                               JsonObject flatJsonObject, String fhirPath, 
                                               Resource targetResource) {
            log.info("Applying OpenEHR to FHIR mapping function: {}", mappingCode);
            log.info("OpenEHR Path: {}, FHIR Path: {}", openEhrPath, fhirPath);
            
            // Create FhirContext
            FhirContext fhirContext = FhirContext.forR4();
            
            // Extract resource type and path parts from the FHIR path
            String[] fhirPathParts = fhirPath.split("\\.");
            String resourceType = fhirPathParts[0];
            String propertyPath = fhirPath.substring(resourceType.length() + 1);
            
            // Create target resource if not provided
            if (targetResource == null && resourceType != null && !resourceType.isEmpty()) {
                try {
                    // Try to instantiate the resource class dynamically
                    Class<?> resourceClass = Class.forName("org.hl7.fhir.r4.model." + resourceType);
                    targetResource = (Resource) resourceClass.getDeclaredConstructor().newInstance();
                    log.info("Created new resource of type: {}", resourceType);
                } catch (Exception e) {
                    log.error("Failed to create resource of type: {}", resourceType, e);
                }
            }
            
            // Track if we modified the target resource
            boolean targetResourceModified = false;
            
            // Dispatch to the appropriate mapping function based on the mappingCode
            Object result = null;
            switch (mappingCode) {
                case "ratio_to_dv_quantity":
                    result = openEhrToFhirRatioToDvQuantity(openEhrPath, flatJsonObject, propertyPath, targetResource);
                    targetResourceModified = true;
                    break;
                case "dosageDurationToAdministrationDuration":
                    result = openEhrToFhirDosageDurationToAdministrationDuration(openEhrPath, flatJsonObject, propertyPath, targetResource);
                    targetResourceModified = true;
                    break;
                // Add other mapping functions as needed
                default:
                    log.warn("Unknown mapping code: {}", mappingCode);
            }
            
            // If result is null but we modified the target resource, return the resource
            if (result == null && targetResourceModified && targetResource != null) {
                log.info("No specific result but target resource was modified, returning: {}", 
                         targetResource.getClass().getSimpleName());
                return targetResource;
            }
            
            return result;
        }
        
        /**
         * Common method to get or create a dosage component on a medication resource
         */
        private org.hl7.fhir.r4.model.Dosage getDosage(Resource targetResource) {
            if (targetResource instanceof org.hl7.fhir.r4.model.MedicationStatement) {
                org.hl7.fhir.r4.model.MedicationStatement ms = 
                    (org.hl7.fhir.r4.model.MedicationStatement) targetResource;
                
                org.hl7.fhir.r4.model.Dosage dosage = ms.getDosageFirstRep();
                if (dosage == null) {
                    dosage = new org.hl7.fhir.r4.model.Dosage();
                    ms.addDosage(dosage);
                }
                return dosage;
            } else if (targetResource instanceof org.hl7.fhir.r4.model.MedicationRequest) {
                org.hl7.fhir.r4.model.MedicationRequest mr = 
                    (org.hl7.fhir.r4.model.MedicationRequest) targetResource;
                
                org.hl7.fhir.r4.model.Dosage dosage = mr.getDosageInstructionFirstRep();
                if (dosage == null) {
                    dosage = new org.hl7.fhir.r4.model.Dosage();
                    mr.addDosageInstruction(dosage);
                }
                return dosage;
            }
            return null;
        }
        
        /**
         * Common method to set timing on a dosage
         */
        private void setTimingOnDosage(org.hl7.fhir.r4.model.Dosage dosage, int value, String unit) {
            org.hl7.fhir.r4.model.Timing timing = dosage.getTiming();
            if (timing == null) {
                timing = new org.hl7.fhir.r4.model.Timing();
                dosage.setTiming(timing);
            }
            
            org.hl7.fhir.r4.model.Timing.TimingRepeatComponent repeat = timing.getRepeat();
            if (repeat == null) {
                repeat = new org.hl7.fhir.r4.model.Timing.TimingRepeatComponent();
                timing.setRepeat(repeat);
            }
            
            repeat.setFrequency(1);
            repeat.setPeriod(value);
            repeat.setPeriodUnit(convertToUnitsOfTime(unit));
        }
        
        /**
         * Converts OpenEHR DV_QUANTITY to FHIR Ratio
         * For medication dosage, mapping the magnitude and unit to the Ratio's numerator
         */
        private Object openEhrToFhirRatioToDvQuantity(String openEhrPath, JsonObject flatJsonObject, 
                                                     String fhirPath, Resource targetResource) {
            log.info("Converting OpenEHR DV_QUANTITY to FHIR Ratio");
            
            try {
                // Extract the base path (without the |magnitude or |unit suffix)
                String basePath = openEhrPath;
                if (basePath.contains("|")) {
                    basePath = basePath.substring(0, basePath.lastIndexOf("|"));
                }
                
                // Now we need to find the magnitude and unit values from the flat JSON
                String magnitudePath = basePath + "/quantity_value|magnitude";
                String unitPath = basePath + "/quantity_value|unit";
                
                log.info("Looking for magnitude at path: {}", magnitudePath);
                log.info("Looking for unit at path: {}", unitPath);
                
                // Extract values from the flat JSON
                double magnitude = 0.0;
                String unit = "";
                
                if (flatJsonObject.has(magnitudePath)) {
                    magnitude = flatJsonObject.get(magnitudePath).getAsDouble();
                }
                
                if (flatJsonObject.has(unitPath)) {
                    unit = flatJsonObject.get(unitPath).getAsString();
                }
                
                log.info("Extracted magnitude: {}, unit: {}", magnitude, unit);
                
                // Create a new Ratio with the numerator populated
                Ratio ratio = new Ratio();
                
                // Set the numerator
                Quantity numerator = new Quantity();
                numerator.setValue(magnitude);
                numerator.setUnit(unit);
                numerator.setSystem("http://unitsofmeasure.org");
                numerator.setCode(unit);
                ratio.setNumerator(numerator);
                
                // For now, create an empty denominator (will be filled by duration mapping if needed)
                Quantity denominator = new Quantity();
                denominator.setValue(1);
                denominator.setUnit("h");
                denominator.setSystem("http://unitsofmeasure.org");
                denominator.setCode("h");
                ratio.setDenominator(denominator);
                
                log.info("Created Ratio with numerator value: {}, unit: {}", magnitude, unit);
                
                // If we have a target resource, try to set this value directly
                if (targetResource != null) {
                    try {
                        log.info("Target resource type: {}", targetResource.getClass().getSimpleName());
                        org.hl7.fhir.r4.model.Dosage dosage = getDosage(targetResource);
                        
                        if (dosage != null) {
                            // Add dose and rate
                            org.hl7.fhir.r4.model.Dosage.DosageDoseAndRateComponent doseAndRate = 
                                dosage.addDoseAndRate();
                            
                            // Set as rate using the ratio
                            doseAndRate.setRate(ratio);
                            log.info("Set rate as Ratio on dosage");
                            
                            // Also set as dose if the path suggests it
                            if (fhirPath.contains("dose")) {
                                doseAndRate.setDose(numerator);
                                log.info("Also set dose quantity");
                            }
                            
                            // Return the entire resource to ensure it gets included in the bundle
                            return targetResource;
                        }
                        
                        // For any other resource type, just return the ratio
                        log.info("Target is not a medication resource, returning ratio directly");
                        return ratio;
                    } catch (Exception e) {
                        log.error("Error setting ratio on target resource: {}", e.getMessage(), e);
                    }
                }
                
                return ratio;
                
            } catch (Exception e) {
                log.error("Error mapping OpenEHR DV_QUANTITY to FHIR Ratio", e);
                return null;
            }
        }
        
        /**
         * Converts OpenEHR Duration to FHIR Ratio's denominator
         * Extracts duration from ISO 8601 format and maps to FHIR Ratio denominator
         */
        private Object openEhrToFhirDosageDurationToAdministrationDuration(String openEhrPath, JsonObject flatJsonObject, 
                                                                         String fhirPath, Resource targetResource) {
            log.info("Converting OpenEHR Duration to FHIR Ratio Denominator");
            
            try {
                // Extract the ISO 8601 duration from the flat JSON
                if (!flatJsonObject.has(openEhrPath)) {
                    log.warn("No duration found at path: {}", openEhrPath);
                    return null;
                }
                
                String isoDuration = flatJsonObject.get(openEhrPath).getAsString();
                log.info("Found ISO 8601 duration: {}", isoDuration);
                
                // Parse the ISO 8601 duration
                int value = 0;
                String unit = "";
                
                // Check for hours (most common case)
                if (isoDuration.contains("H")) {
                    value = extractNumericValue(isoDuration, "H");
                    unit = "h";
                } 
                // Check for minutes
                else if (isoDuration.contains("M") && isoDuration.contains("T")) {
                    value = extractNumericValue(isoDuration, "M");
                    unit = "min";
                } 
                // Check for seconds
                else if (isoDuration.contains("S")) {
                    value = extractNumericValue(isoDuration, "S");
                    unit = "s";
                } 
                // Check for days
                else if (isoDuration.contains("D")) {
                    value = extractNumericValue(isoDuration, "D");
                    unit = "d";
                } 
                // Check for weeks
                else if (isoDuration.contains("W")) {
                    value = extractNumericValue(isoDuration, "W");
                    unit = "wk";
                } 
                // Check for months
                else if (isoDuration.contains("M") && !isoDuration.contains("T")) {
                    value = extractNumericValue(isoDuration, "M");
                    unit = "mo";
                } 
                // Check for years
                else if (isoDuration.contains("Y")) {
                    value = extractNumericValue(isoDuration, "Y");
                    unit = "a";
                }
                
                log.info("Parsed duration: {} {}", value, unit);
                
                // Create the denominator
                Quantity denominator = new Quantity();
                denominator.setValue(value);
                denominator.setUnit(unit);
                denominator.setSystem("http://unitsofmeasure.org");
                denominator.setCode(unit);
                
                // Create or update the Ratio
                Ratio ratio = new Ratio();
                
                // If ratio doesn't have a numerator yet, create one
                Quantity numerator = new Quantity();
                numerator.setValue(1);
                ratio.setNumerator(numerator);
                ratio.setDenominator(denominator);
                
                log.info("Created Ratio with denominator value: {}, unit: {}", value, unit);
                
                // If we have a target resource, try to set this value directly
                if (targetResource != null) {
                    try {
                        log.info("Target resource type: {}", targetResource.getClass().getSimpleName());
                        org.hl7.fhir.r4.model.Dosage dosage = getDosage(targetResource);
                        
                        if (dosage != null) {
                            // Handle different paths
                            if (fhirPath.contains("doseAndRate.rate")) {
                                // For rate, we're looking at a dose over time
                                log.info("Path appears to be for doseAndRate.rate, setting as ratio");
                                
                                // In this case, we'll use the created Ratio directly
                                org.hl7.fhir.r4.model.Dosage.DosageDoseAndRateComponent doseAndRate = 
                                    dosage.addDoseAndRate();
                                doseAndRate.setRate(ratio);
                                
                                log.info("Successfully set dose rate");
                            } else {
                                // Set frequency/timing
                                setTimingOnDosage(dosage, value, unit);
                                log.info("Set timing frequency");
                            }
                            
                            // Return the entire resource to ensure it gets included in the bundle
                            return targetResource;
                        }
                        
                        // For any other resource type, just return the ratio
                        log.info("Target is not a medication resource, returning ratio directly");
                        return ratio;
                    } catch (Exception e) {
                        log.error("Error setting duration on target resource: {}", e.getMessage(), e);
                    }
                }
                
                return ratio;
                
            } catch (Exception e) {
                log.error("Error mapping OpenEHR Duration to FHIR Ratio denominator", e);
                return null;
            }
        }

        /**
         * Converts a UCUM time unit to FHIR's Timing.UnitsOfTime
         */
        private org.hl7.fhir.r4.model.Timing.UnitsOfTime convertToUnitsOfTime(String unit) {
            switch (unit.toLowerCase()) {
                case "s":
                case "sec":
                    return org.hl7.fhir.r4.model.Timing.UnitsOfTime.S;
                case "min":
                    return org.hl7.fhir.r4.model.Timing.UnitsOfTime.MIN;
                case "h":
                case "hr":
                    return org.hl7.fhir.r4.model.Timing.UnitsOfTime.H;
                case "d":
                case "day":
                    return org.hl7.fhir.r4.model.Timing.UnitsOfTime.D;
                case "wk":
                case "week":
                    return org.hl7.fhir.r4.model.Timing.UnitsOfTime.WK;
                case "mo":
                case "month":
                    return org.hl7.fhir.r4.model.Timing.UnitsOfTime.MO;
                case "a":
                case "yr":
                case "year":
                    return org.hl7.fhir.r4.model.Timing.UnitsOfTime.A;
                default:
                    return org.hl7.fhir.r4.model.Timing.UnitsOfTime.H; // Default to hours
            }
        }

        /**
         * Converts FHIR dosage duration to OpenEHR administration duration
         * This function extracts the time duration from the denominator of a FHIR Ratio
         * and maps it to an OpenEHR DV_DURATION in ISO 8601 format
         */
        private boolean dosageDurationToAdministrationDuration(String openEhrPath, Object fhirValue, 
                                                          String openEhrType, Object flatComposition) {
            log.info("Converting dosage duration to administration duration");
            
            try {
                // Check if the FHIR value is present and is a Ratio
                if (fhirValue == null) {
                    log.warn("No FHIR value provided for dosage duration");
                    return false;
                }
                
                if (!(fhirValue instanceof Ratio)) {
                    log.warn("Expected Ratio type for dosage duration but got: {}", fhirValue.getClass().getName());
                    return false;
                }
                
                Ratio ratio = (Ratio) fhirValue;
                JsonObject flatJson = (JsonObject) flatComposition;
                
                // Extract denominator which contains the time component
                Quantity denominator = ratio.getDenominator();
                
                if (denominator == null || denominator.getValue() == null) {
                    log.warn("Incomplete ratio value: denominator missing values");
                    return false;
                }
                
                // Get the denominator value and unit
                double value = denominator.getValue().doubleValue();
                String unit = denominator.getUnit() != null ? denominator.getUnit() : denominator.getCode();
                
                // Check if the unit is a time unit before mapping
                String timeUnit = mapToTimeUnit(unit);
                if (timeUnit == null) {
                    log.info("Denominator unit '{}' is not a time unit, skipping duration mapping", unit);
                    return false;
                }
                
                // Convert to ISO 8601 duration format: P[nnY][nnM][nnW][nnD][T[nnH][nnM][nnS]]
                int timeValue = (int) Math.round(value);
                String isoDuration = formatIso8601Duration(timeValue, timeUnit);
                
                // Add the ISO 8601 duration to the flat composition
                flatJson.add(openEhrPath, new JsonPrimitive(isoDuration));
                
                log.info("Mapped duration: {} {} to {} with ISO 8601 format: {}", 
                         timeValue, timeUnit, openEhrPath, isoDuration);
                return true;
                
            } catch (Exception e) {
                log.error("Error mapping dosage duration to administration duration", e);
                return false;
            }
        }
        
        /**
         * Formats a time value and unit into ISO 8601 duration format
         */
        private String formatIso8601Duration(int value, String unit) {
            StringBuilder duration = new StringBuilder("P");
            
            switch (unit) {
                case "year":
                    duration.append(value).append("Y");
                    break;
                case "month":
                    duration.append(value).append("M");
                    break;
                case "week":
                    duration.append(value).append("W");
                    break;
                case "day":
                    duration.append(value).append("D");
                    break;
                case "hour":
                    duration.append("T").append(value).append("H");
                    break;
                case "minute":
                    duration.append("T").append(value).append("M");
                    break;
                case "second":
                    duration.append("T").append(value).append("S");
                    break;
                default:
                    // Default to hours if unit is not recognized
                    duration.append("T").append(value).append("H");
            }
            
            return duration.toString();
        }
        
        /**
         * Maps a UCUM or common time unit to OpenEHR DV_DURATION time unit
         */
        private String mapToTimeUnit(String unit) {
            if (unit == null) {
                return null;
            }
            
            unit = unit.toLowerCase().trim();
            
            switch (unit) {
                case "a":
                case "yr":
                case "year":
                case "years":
                    return "year";
                case "mo":
                case "month":
                case "months":
                    return "month";
                case "wk":
                case "week":
                case "weeks":
                    return "week";
                case "d":
                case "day":
                case "days":
                    return "day";
                case "h":
                case "hr":
                case "hour":
                case "hours":
                    return "hour";
                case "min":
                case "minute":
                case "minutes":
                    return "minute";
                case "s":
                case "sec":
                case "second":
                case "seconds":
                    return "second";
                default:
                    return null;
            }
        }

        /**
         * Converts FHIR Ratio to OpenEHR DV_QUANTITY
         * For medication dosage, we map the numerator of the Ratio to the DV_QUANTITY
         */
        private boolean ratio_to_dv_quantity(String openEhrPath, Object fhirValue, 
                                     String openEhrType, Object flatComposition) {
            log.info("Converting FHIR Ratio to OpenEHR DV_QUANTITY");
            
            try {
                // Check if the FHIR value is present and is a Ratio
                if (fhirValue == null) {
                    log.warn("No FHIR value provided for ratio conversion");
                    return false;
                }
                
                if (!(fhirValue instanceof Ratio)) {
                    log.warn("Expected Ratio type but got: {}", fhirValue.getClass().getName());
                    return false;
                }
                
                Ratio ratio = (Ratio) fhirValue;
                JsonObject flatJson = (JsonObject) flatComposition;
                
                // Extract values from numerator
                Quantity numerator = ratio.getNumerator();
                
                if (numerator == null || numerator.getValue() == null) {
                    log.warn("Incomplete ratio value: numerator missing values");
                    return false;
                }
                
                // Get the magnitude from the numerator
                double magnitude = numerator.getValue().doubleValue();
                
                // Get the unit from the numerator
                String unit = numerator.getUnit() != null ? numerator.getUnit() : numerator.getCode();
                
                // Set the magnitude and unit in the flat composition with quantity_value suffix
                flatJson.add(openEhrPath + "/quantity_value|magnitude", new JsonPrimitive(magnitude));
                flatJson.add(openEhrPath + "/quantity_value|unit", new JsonPrimitive(unit));
                
                log.info("Mapped Ratio numerator to DV_QUANTITY: path={}, magnitude={}, unit={}", 
                        openEhrPath, magnitude, unit);
                return true;
                
            } catch (Exception e) {
                log.error("Error mapping FHIR Ratio to OpenEHR DV_QUANTITY", e);
                return false;
            }
        }
        
        /**
         * Helper method to extract numeric value from ISO 8601 duration string
         */
        private int extractNumericValue(String durationStr, String unitChar) {
            try {
                Pattern pattern = Pattern.compile("(\\d+)" + unitChar);
                Matcher matcher = pattern.matcher(durationStr);
                if (matcher.find()) {
                    return Integer.parseInt(matcher.group(1));
                }
                
                // For cases like "PT3H" where it might be in a different format
                if (unitChar.equals("H")) {
                    pattern = Pattern.compile("PT(\\d+)H");
                    matcher = pattern.matcher(durationStr);
                    if (matcher.find()) {
                        return Integer.parseInt(matcher.group(1));
                    }
                }
                
                if (unitChar.equals("M")) {
                    pattern = Pattern.compile("PT(\\d+)M");
                    matcher = pattern.matcher(durationStr);
                    if (matcher.find()) {
                        return Integer.parseInt(matcher.group(1));
                    }
                }
                
                if (unitChar.equals("S")) {
                    pattern = Pattern.compile("PT(\\d+)S");
                    matcher = pattern.matcher(durationStr);
                    if (matcher.find()) {
                        return Integer.parseInt(matcher.group(1));
                    }
                }
                
                log.warn("Could not extract numeric value for unit {} from duration: {}", unitChar, durationStr);
                return 0;
            } catch (Exception e) {
                log.error("Error extracting numeric value from duration: {}", durationStr, e);
                return 0;
            }
        }
    }
} 
