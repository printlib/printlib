package qz.build.provision;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import qz.build.provision.params.Os;
import qz.build.provision.params.Phase;
import qz.build.provision.params.Type;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Builder for provision steps
 */
public class ProvisionBuilder {
    private static final Logger log = LogManager.getLogger(ProvisionBuilder.class);
    public static final String BUILD_PROVISION_FILE = "provision.json";
    
    private List<Step> steps;
    private String targetOs;
    private String targetArch;
    private boolean saveJson;

    public ProvisionBuilder(File jsonFile, String targetOs, String targetArch) throws IOException, JSONException {
        this.targetOs = targetOs;
        this.targetArch = targetArch;
        this.steps = new ArrayList<>();
        
        if (jsonFile != null && jsonFile.exists()) {
            loadFromFile(jsonFile);
        }
    }

    public ProvisionBuilder(String type, String phase, String os, String arch, String data, String args, String description, String[] argArray) {
        this.steps = new ArrayList<>();
        
        Step step = new Step();
        
        // Set type
        try {
            step.setType(Type.valueOf(type.toUpperCase()));
        } catch (IllegalArgumentException e) {
            log.error("Invalid step type: " + type);
            throw e;
        }
        
        // Set data
        step.setData(data);
        step.setDataString(data);
        
        // Set phase
        if (phase != null) {
            try {
                step.setPhase(Phase.valueOf(phase.toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.error("Invalid phase: " + phase);
                throw e;
            }
        }
        
        // Set target OS
        if (os != null) {
            try {
                step.setTargetOs(Os.valueOf(os.toUpperCase()));
            } catch (IllegalArgumentException e) {
                step.setTargetOs(Os.bestMatch(os));
            }
        }
        
        // Set args if provided
        if (args != null) {
            step.setArgs(args);
        }
        
        // Set description if provided
        if (description != null) {
            step.setDescription(description);
        }
        
        // Set arg array if provided
        if (argArray != null) {
            step.setArgArray(argArray);
        }
        
        this.steps.add(step);
    }

    private void loadFromFile(File jsonFile) throws IOException, JSONException {
        String content = new String(Files.readAllBytes(jsonFile.toPath()));
        JSONArray jsonArray = new JSONArray(content);
        
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonStep = jsonArray.getJSONObject(i);
            Step step = Step.parse(jsonStep, jsonFile.getParentFile().toPath());
            steps.add(step);
        }
    }

    public void saveJson(boolean saveJson) {
        this.saveJson = saveJson;
        
        if (saveJson) {
            try {
                saveToFile();
            } catch (Exception e) {
                log.error("Failed to save provision file", e);
            }
        }
    }

    private void saveToFile() throws IOException, JSONException {
        JSONArray jsonArray = new JSONArray();
        
        for (Step step : steps) {
            JSONObject jsonStep = new JSONObject();
            jsonStep.put("type", step.getType().name().toLowerCase());
            
            if (step.getData() != null) {
                jsonStep.put("data", step.getData());
            }
            
            if (step.getPhase() != null) {
                jsonStep.put("phase", step.getPhase().name().toLowerCase());
            }
            
            if (step.getTargetOs() != null) {
                jsonStep.put("os", step.getTargetOs().name().toLowerCase());
            }
            
            jsonStep.put("elevated", step.isElevated());
            
            jsonArray.put(jsonStep);
        }
        
        File outputFile = new File(BUILD_PROVISION_FILE);
        Files.write(outputFile.toPath(), jsonArray.toString(2).getBytes(), 
                   StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        
        log.info("Saved provision file: " + outputFile.getAbsolutePath());
    }

    public String getJson() {
        try {
            JSONArray jsonArray = new JSONArray();
            
            for (Step step : steps) {
                JSONObject jsonStep = new JSONObject();
                jsonStep.put("type", step.getType().name().toLowerCase());
                
                if (step.getData() != null) {
                    jsonStep.put("data", step.getData());
                }
                
                if (step.getPhase() != null) {
                    jsonStep.put("phase", step.getPhase().name().toLowerCase());
                }
                
                if (step.getTargetOs() != null) {
                    jsonStep.put("os", step.getTargetOs().name().toLowerCase());
                }
                
                jsonStep.put("elevated", step.isElevated());
                
                jsonArray.put(jsonStep);
            }
            
            return jsonArray.toString(2);
        } catch (JSONException e) {
            log.error("Failed to generate JSON", e);
            return "{}";
        }
    }

    public List<Step> getSteps() {
        return steps;
    }

    public void addStep(Step step) {
        this.steps.add(step);
    }
}
