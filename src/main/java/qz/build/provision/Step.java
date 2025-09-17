package qz.build.provision;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import qz.build.provision.params.Os;
import qz.build.provision.params.Phase;
import qz.build.provision.params.Type;
import qz.build.provision.params.types.Remover;
import qz.build.provision.params.types.Script;
import qz.build.provision.params.types.Software;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Represents a provision step
 */
public class Step {
    private Type type;
    private Object data;
    private String dataString;
    private Path relativePath;
    private Os targetOs;
    private Phase phase;
    private boolean elevated;
    private String args;
    private String description;
    private String[] argArray;
    private String name;
    private Class<?> relativeClass;

    public Step() {}

    public Step(Type type, Object data) {
        this.type = type;
        this.data = data;
        if (data != null) {
            this.dataString = data.toString();
        }
    }

    /**
     * Parse a Step from JSON
     */
    public static Step parse(JSONObject jsonStep, Object relativeObject) throws JSONException {
        Step step = new Step();
        
        // Parse type
        if (jsonStep.has("type")) {
            String typeStr = jsonStep.getString("type").toUpperCase();
            try {
                step.type = Type.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                throw new JSONException("Invalid step type: " + typeStr);
            }
        } else {
            throw new JSONException("Step type is required");
        }

        // Parse data
        if (jsonStep.has("data")) {
            Object dataObj = jsonStep.get("data");
            step.data = dataObj;
            step.dataString = dataObj.toString();
        }

        // Parse target OS
        if (jsonStep.has("os")) {
            String osStr = jsonStep.getString("os").toUpperCase();
            try {
                step.targetOs = Os.valueOf(osStr);
            } catch (IllegalArgumentException e) {
                // Try to match by best match
                step.targetOs = Os.bestMatch(osStr);
            }
        }

        // Parse phase
        if (jsonStep.has("phase")) {
            String phaseStr = jsonStep.getString("phase").toUpperCase();
            try {
                step.phase = Phase.valueOf(phaseStr);
            } catch (IllegalArgumentException e) {
                throw new JSONException("Invalid phase: " + phaseStr);
            }
        }

        // Parse elevated flag
        if (jsonStep.has("elevated")) {
            step.elevated = jsonStep.getBoolean("elevated");
        }

        // Set relative path if provided
        if (relativeObject != null) {
            if (relativeObject instanceof Path) {
                step.relativePath = (Path) relativeObject;
            } else if (relativeObject instanceof String) {
                step.relativePath = Paths.get((String) relativeObject);
            }
        }

        return step;
    }

    // Getters and setters
    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
        if (data != null) {
            this.dataString = data.toString();
        }
    }

    public String getDataAsString() {
        return dataString;
    }

    public void setDataString(String dataString) {
        this.dataString = dataString;
    }

    public Path getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(Path relativePath) {
        this.relativePath = relativePath;
    }

    public Os getTargetOs() {
        return targetOs;
    }

    public void setTargetOs(Os targetOs) {
        this.targetOs = targetOs;
    }

    public Phase getPhase() {
        return phase;
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
    }

    public boolean isElevated() {
        return elevated;
    }

    public void setElevated(boolean elevated) {
        this.elevated = elevated;
    }

    public String getArgs() {
        return args;
    }

    public void setArgs(String args) {
        this.args = args;
    }

    /**
     * Get args as a list of strings, splitting by spaces
     */
    public java.util.List<String> getArgsList() {
        if (args == null || args.trim().isEmpty()) {
            return new java.util.ArrayList<>();
        }
        return java.util.Arrays.asList(args.trim().split("\\s+"));
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String[] getArgArray() {
        return argArray;
    }

    public void setArgArray(String[] argArray) {
        this.argArray = argArray;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Class<?> getRelativeClass() {
        return relativeClass;
    }

    public void setRelativeClass(Class<?> relativeClass) {
        this.relativeClass = relativeClass;
    }

    public Os getOs() {
        return targetOs;
    }

    public boolean usingPath() {
        return relativePath != null;
    }

    public boolean usingClass() {
        return relativeClass != null;
    }

    /**
     * Get typed data based on step type
     */
    public Script getScript() {
        if (type == Type.SCRIPT && data instanceof Script) {
            return (Script) data;
        }
        return null;
    }

    public Software getSoftware() {
        if (type == Type.SOFTWARE && data instanceof Software) {
            return (Software) data;
        }
        return null;
    }

    public Remover getRemover() {
        if (type == Type.REMOVER && data instanceof Remover) {
            return (Remover) data;
        }
        return null;
    }

    @Override
    public String toString() {
        return "Step{" +
                "type=" + type +
                ", data=" + data +
                ", targetOs=" + targetOs +
                ", phase=" + phase +
                ", elevated=" + elevated +
                '}';
    }
}
