package app.test.migrator.matching.util;

public class AssertionJsonNode {
    private String id;
    private String activityName;

    public AssertionJsonNode(String id, String activityName) {
        this.id = id;
        this.activityName = activityName;
    }

    public String getId() { return id; }
    public String getActivityName() { return activityName; }
}