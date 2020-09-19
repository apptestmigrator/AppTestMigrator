package app.test.migrator.matching.util;

public class JsonNode {
    private String id;
    private String activityName;
    private String type;

    public JsonNode(String id, String activityName, String type) {
        this.id = id;
        this.activityName = activityName;
        this.type = type;
    }

    public String getId() { return id; }
    public String getActivityName() { return activityName; }
    public String getType() { return type; }
}
