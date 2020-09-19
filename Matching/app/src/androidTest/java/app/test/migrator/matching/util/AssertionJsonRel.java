package app.test.migrator.matching.util;

public class AssertionJsonRel {
    private String id;
    private String text;
    private String clazz;
    private String contentDesc;

    public AssertionJsonRel(String id, String text, String clazz, String contentDesc) {
        this.id = id;
        this.contentDesc = contentDesc;
        this.text = text;
        this.clazz = clazz;
    }

    public AssertionJsonRel() {
        this.id = null;
        this.contentDesc = null;
        this.text = null;
        this.clazz = null;
    }

    public String getId() { return id;  }
    public String getContentDesc() { return contentDesc;  }
    public String getText() { return text;  }
    public String getClazz() { return clazz;  }
}