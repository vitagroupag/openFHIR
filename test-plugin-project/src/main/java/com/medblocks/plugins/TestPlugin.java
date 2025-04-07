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
import org.hl7.fhir.r4.model.Timing;
import org.hl7.fhir.r4.model.Range;
import org.hl7.fhir.r4.hapi.fluentpath.FhirPathR4;
import ca.uhn.fhir.context.FhirContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Supplier;

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
                case "timingToDaily_NonDaily":
                    return timingToDaily_NonDaily(openEhrPath, fhirValue, openEhrType, flatComposition);
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
            log.info("OpenEHR to FHIR mapping is currently disabled");
            return null;
            
            /* 
            // Commenting out OpenEHR to FHIR mapping as requested
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
            */
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
         * Create a quantity with the specified values
         */
        private Quantity createQuantity(double value, String unit) {
            Quantity quantity = new Quantity();
            quantity.setValue(value);
            quantity.setUnit(unit);
            quantity.setSystem("http://unitsofmeasure.org");
            quantity.setCode(unit);
            return quantity;
        }
        
        /**
         * Create a Ratio with numerator and denominator
         */
        private Ratio createRatio(double numeratorValue, String numeratorUnit, 
                                double denominatorValue, String denominatorUnit) {
            Ratio ratio = new Ratio();
            ratio.setNumerator(createQuantity(numeratorValue, numeratorUnit));
            ratio.setDenominator(createQuantity(denominatorValue, denominatorUnit));
            return ratio;
        }
        
        /**
         * Set rate on a dosage component
         */
        private void setRateOnDosage(org.hl7.fhir.r4.model.Dosage dosage, Ratio ratio, String fhirPath) {
            org.hl7.fhir.r4.model.Dosage.DosageDoseAndRateComponent doseAndRate = 
                dosage.addDoseAndRate();
            
            // Set as rate using the ratio
            doseAndRate.setRate(ratio);
            log.info("Set rate as Ratio on dosage");
            
            // Also set as dose if the path suggests it
            if (fhirPath != null && fhirPath.contains("dose")) {
                doseAndRate.setDose(ratio.getNumerator());
                log.info("Also set dose quantity");
            }
        }
        
        /**
         * Apply dosage settings to target resource based on fhirPath and values
         */
        private Object applyDosageSettings(Resource targetResource, Ratio ratio, String fhirPath) {
            if (targetResource == null) {
                return ratio;
            }
            
            try {
                log.info("Target resource type: {}", targetResource.getClass().getSimpleName());
                org.hl7.fhir.r4.model.Dosage dosage = getDosage(targetResource);
                
                if (dosage != null) {
                    // Handle different paths
                    if (fhirPath.contains("doseAndRate.rate")) {
                        setRateOnDosage(dosage, ratio, fhirPath);
                        log.info("Successfully set dose rate");
                    } else {
                        // Get denominator value and unit for timing
                        int value = ratio.getDenominator().getValue().intValue();
                        String unit = ratio.getDenominator().getCode();
                        
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
                log.error("Error applying dosage settings to resource: {}", e.getMessage(), e);
                return ratio;
            }
        }
        
        /**
         * Validates a FHIR Ratio and extracts its components
         */
        private ValidationResult validateRatio(Object fhirValue, String context) {
            ValidationResult result = new ValidationResult();
            
            if (fhirValue == null) {
                log.warn("No FHIR value provided for {}", context);
                result.success = false;
                return result;
            }
            
            if (!(fhirValue instanceof Ratio)) {
                log.warn("Expected Ratio type for {} but got: {}", context, fhirValue.getClass().getName());
                result.success = false;
                return result;
            }
            
            Ratio ratio = (Ratio) fhirValue;
            result.ratio = ratio;
            
            // Extract numerator data
            Quantity numerator = ratio.getNumerator();
            if (numerator != null && numerator.getValue() != null) {
                result.numeratorValue = numerator.getValue().doubleValue();
                result.numeratorUnit = numerator.getUnit() != null ? numerator.getUnit() : numerator.getCode();
            } else {
                result.numeratorValid = false;
            }
            
            // Extract denominator data
            Quantity denominator = ratio.getDenominator();
            if (denominator != null && denominator.getValue() != null) {
                result.denominatorValue = denominator.getValue().doubleValue();
                result.denominatorUnit = denominator.getUnit() != null ? denominator.getUnit() : denominator.getCode();
            } else {
                result.denominatorValid = false;
            }
            
            result.success = true;
            return result;
        }
        
        /**
         * Helper class to store validation results
         */
        private static class ValidationResult {
            boolean success = true;
            Ratio ratio;
            
            double numeratorValue;
            String numeratorUnit = "";
            boolean numeratorValid = true;
            
            double denominatorValue;
            String denominatorUnit = "";
            boolean denominatorValid = true;
        }
        
        /**
         * Extracts values from flatJsonObject safely
         */
        private <T> T getValueFromJson(JsonObject jsonObject, String path, Class<T> type, T defaultValue) {
            if (jsonObject == null || !jsonObject.has(path)) {
                return defaultValue;
            }
            
            try {
                JsonElement element = jsonObject.get(path);
                if (element == null || element.isJsonNull()) {
                    return defaultValue;
                }
                
                if (type == Double.class) {
                    return type.cast(element.getAsDouble());
                } else if (type == Integer.class) {
                    return type.cast(element.getAsInt());
                } else if (type == String.class) {
                    return type.cast(element.getAsString());
                } else if (type == Boolean.class) {
                    return type.cast(element.getAsBoolean());
                }
            } catch (Exception e) {
                log.warn("Error extracting {} from path {}: {}", type.getSimpleName(), path, e.getMessage());
            }
            
            return defaultValue;
        }
        
        /**
         * Sets a value in a flat JSON object
         */
        private void setValueInJson(JsonObject jsonObject, String path, Object value) {
            if (jsonObject == null) {
                return;
            }
            
            try {
                if (value instanceof String) {
                    jsonObject.add(path, new JsonPrimitive((String) value));
                } else if (value instanceof Number) {
                    jsonObject.add(path, new JsonPrimitive((Number) value));
                } else if (value instanceof Boolean) {
                    jsonObject.add(path, new JsonPrimitive((Boolean) value));
                }
            } catch (Exception e) {
                log.error("Failed to set value at path {}: {}", path, e.getMessage());
            }
        }
        
        /**
         * Executes a code block with standard exception handling and logging
         */
        private <T> T executeWithExceptionHandling(String operationName, Supplier<T> operation, T defaultValue) {
            try {
                return operation.get();
            } catch (Exception e) {
                log.error("Error in operation {}: {}", operationName, e.getMessage(), e);
                return defaultValue;
            }
        }

        /**
         * Mapping function for FHIR Timing to OpenEHR timing_daily cluster
         * Maps frequency, timeOfDay, period, and other timing elements
         */
        private boolean timingToDaily_NonDaily(String openEhrPath, Object fhirValue, 
                                      String openEhrType, Object flatComposition) {
            return executeWithExceptionHandling("FHIR Timing to OpenEHR timing_daily", () -> {
                log.info("Converting FHIR Timing to OpenEHR timing_daily");
                
                if (!(fhirValue instanceof Timing)) {
                    log.warn("Expected Timing type but got: {}", fhirValue != null ? fhirValue.getClass().getName() : "null");
                    return false;
                }
                
                Timing timing = (Timing) fhirValue;
                JsonObject flatJson = (JsonObject) flatComposition;
                boolean success = false;
                
                // Only proceed if timing has repeat component
                if (timing.hasRepeat()) {
                    Timing.TimingRepeatComponent repeat = timing.getRepeat();
                    
                    // Map specific time (timeOfDay)
                    if (repeat.hasTimeOfDay() && !repeat.getTimeOfDay().isEmpty()) {
                        String timeOfDay = repeat.getTimeOfDay().get(0).getValue();
                        if (timeOfDay != null) {
                            setValueInJson(flatJson, openEhrPath + "/items[at0004]", timeOfDay);
                            log.info("Mapped specific time: {}", timeOfDay);
                            success = true;
                        }
                    }
                    
                    // Map frequency
                    if (repeat.hasFrequency()) {
                        int frequency = repeat.getFrequency();
                        String frequencyText = String.valueOf(frequency);
                        
                        // Check if frequencyMax exists for range notation
                        if (repeat.hasFrequencyMax()) {
                            int frequencyMax = repeat.getFrequencyMax();
                            frequencyText = frequency + "-" + frequencyMax;
                        }
                        
                        // Add "times per day" if periodUnit is day
                        if (repeat.hasPeriodUnit() && repeat.getPeriodUnit() == Timing.UnitsOfTime.D) {
                            frequencyText += " times per day";
                        } else if (repeat.hasPeriodUnit()) {
                            frequencyText += " times per " + repeat.getPeriodUnit().getDisplay().toLowerCase();
                        }
                        
                        setValueInJson(flatJson, openEhrPath + "/items[at0003]", frequencyText);
                        log.info("Mapped frequency: {}", frequencyText);
                        success = true;
                    }
                    
                    // Map interval (period)
                    if (repeat.hasPeriod()) {
                        double period = repeat.getPeriod().doubleValue();
                        String intervalText = "every ";
                        
                        // Check if periodMax exists for range notation
                        if (repeat.hasPeriodMax()) {
                            double periodMax = repeat.getPeriodMax().doubleValue();
                            intervalText += period + "-" + periodMax + " ";
                        } else {
                            intervalText += period + " ";
                        }
                        
                        // Add unit if available
                        if (repeat.hasPeriodUnit()) {
                            String unit = repeat.getPeriodUnit().getDisplay().toLowerCase();
                            // Handle singular/plural
                            if (period == 1) {
                                intervalText += unit;
                            } else {
                                intervalText += unit + "s";
                            }
                        }
                        
                        setValueInJson(flatJson, openEhrPath + "/items[at0014]", intervalText);
                        log.info("Mapped interval: {}", intervalText);
                        success = true;
                    }
                }
                
                return success;
            }, false);
        }

        /**
         * Converts OpenEHR DV_QUANTITY to FHIR Ratio
         * For medication dosage, mapping the magnitude and unit to the Ratio's numerator
         */
        private Object openEhrToFhirRatioToDvQuantity(String openEhrPath, JsonObject flatJsonObject, 
                                                     String fhirPath, Resource targetResource) {
            // Commented out as requested
            return null;
        }
        
        /**
         * Converts OpenEHR Duration to FHIR Ratio's denominator
         * Extracts duration from ISO 8601 format and maps to FHIR Ratio denominator
         */
        private Object openEhrToFhirDosageDurationToAdministrationDuration(String openEhrPath, JsonObject flatJsonObject, 
                                                                         String fhirPath, Resource targetResource) {
            // Commented out as requested
            return null;
        }

        /**
         * Converts FHIR dosage duration to OpenEHR administration duration
         * This function extracts the time duration from the Timing.repeat component
         * and maps it to an OpenEHR administration duration
         */
        private boolean dosageDurationToAdministrationDuration(String openEhrPath, Object fhirValue, 
                                                          String openEhrType, Object flatComposition) {
            return executeWithExceptionHandling("dosage duration to administration duration", () -> {
                log.info("Converting timing repeat to administration duration");
                
                if (!(fhirValue instanceof Timing.TimingRepeatComponent)) {
                    log.warn("Expected TimingRepeatComponent but got: {}", 
                           fhirValue != null ? fhirValue.getClass().getName() : "null");
                    return false;
                }
                
                Timing.TimingRepeatComponent repeat = (Timing.TimingRepeatComponent) fhirValue;
                JsonObject flatJson = (JsonObject) flatComposition;
                
                // Check if duration exists
                if (!repeat.hasDuration()) {
                    log.info("No duration found in timing repeat");
                    return false;
                }
                
                // Format the duration, with range if durationMax exists
                double duration = repeat.getDuration().doubleValue();
                String durationText = String.valueOf(duration);
                
                if (repeat.hasDurationMax()) {
                    double durationMax = repeat.getDurationMax().doubleValue();
                    durationText = duration + "-" + durationMax;
                }
                
                // Add unit if available
                if (repeat.hasDurationUnit()) {
                    durationText += " " + repeat.getDurationUnit().getDisplay().toLowerCase();
                }
                
                // Add the duration to the flat composition
                setValueInJson(flatJson, openEhrPath, durationText);
                
                log.info("Mapped administration duration: {}", durationText);
                return true;
            }, false);
        }
        
        /**
         * Converts FHIR Ratio to OpenEHR DV_QUANTITY
         * For medication dosage, we map the numerator of the Ratio to the DV_QUANTITY
         */
        private boolean ratio_to_dv_quantity(String openEhrPath, Object fhirValue, 
                                     String openEhrType, Object flatComposition) {
            return executeWithExceptionHandling("FHIR Ratio to OpenEHR DV_QUANTITY", () -> {
                log.info("Converting FHIR Ratio to OpenEHR Administration Rate");
                
                ValidationResult validation = validateRatio(fhirValue, "ratio conversion");
                if (!validation.success || !validation.numeratorValid) {
                    return false;
                }
                
                JsonObject flatJson = (JsonObject) flatComposition;
                
                // Format as numerator/denominator (e.g., "600 mg/h")
                String formattedRate = validation.numeratorValue + " " + validation.numeratorUnit;
                if (validation.denominatorValid) {
                    formattedRate += "/" + validation.denominatorUnit;
                }
                
                // Set the formatted rate directly on the path
                setValueInJson(flatJson, openEhrPath, formattedRate);
                
                log.info("Mapped Ratio to Administration Rate: path={}, value={}", 
                         openEhrPath, formattedRate);
                return true;
            }, false);
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
         * Helper method to parse ISO 8601 duration string into value and unit
         */
        private DurationInfo parseIsoDuration(String isoDuration) {
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
            
            return new DurationInfo(value, unit);
        }
    }

    /**
     * Helper class to store duration information
     */
    private static class DurationInfo {
        int value;
        String unit;
        
        DurationInfo(int value, String unit) {
            this.value = value;
            this.unit = unit;
        }
    }
} 
