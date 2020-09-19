package app.test.migrator.matching.util;

public class JsonRel {
    private String id;
    private String action;
    private String text;
    private String clazz;

    public JsonRel(String id, String action, String text, String clazz) {
        this.id = id;
        this.action = action;
        this.text = text;
        this.clazz = clazz;
    }

    public JsonRel() {
        this.id = null;
        this.action = null;
        this.text = null;
        this.clazz = null;
    }

    public String getId() { return id;  }
    public String getAction() { return action;  }
    public String getText() { return text;  }
    public String getClazz() { return clazz;  }
}
