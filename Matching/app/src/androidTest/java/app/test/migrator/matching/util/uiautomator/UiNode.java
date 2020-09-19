package app.test.migrator.matching.util.uiautomator;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UiNode extends BasicTreeNode {
    private static final Pattern BOUNDS_PATTERN = Pattern
            .compile("\\[-?(\\d+),-?(\\d+)\\]\\[-?(\\d+),-?(\\d+)\\]");
    // use LinkedHashMap to preserve the order of the attributes
    private final Map<String, String> mAttributes = new LinkedHashMap<String, String>();
    private String mDisplayName = "ShouldNotSeeMe";
    private Object[] mCachedAttributesArray;

    public void addAtrribute(String key, String value) {
        mAttributes.put(key, value);
        updateDisplayName();
        if ("bounds".equals(key)) {
            updateBounds(value);
        }
    }

    public Map<String, String> getAttributes() {
        return Collections.unmodifiableMap(mAttributes);
    }

    /**
     * Builds the display name based on attributes of the node
     */
    private void updateDisplayName() {
        StringBuilder builder = new StringBuilder();
        for(String key : mAttributes.keySet()){
            builder.append(key + " : " + mAttributes.get(key) + " , ");
        }
        mDisplayName = builder.toString();
    }

    private void updateBounds(String bounds) {
        Matcher m = BOUNDS_PATTERN.matcher(bounds);
        if (m.matches()) {
            x = Integer.parseInt(m.group(1));
            y = Integer.parseInt(m.group(2));
            width = Integer.parseInt(m.group(3));
            height = Integer.parseInt(m.group(4));

            mAttributes.put("x1", m.group(1));
            mAttributes.put("y1", m.group(2));
            mAttributes.put("width", m.group(3));
            mAttributes.put("height", m.group(4));
            mAttributes.put("x2", Integer.toString(Integer.parseInt(m.group(1)) + Integer.parseInt(m.group(3))));
            mAttributes.put("y2", Integer.toString(Integer.parseInt(m.group(2)) + Integer.parseInt(m.group(4))));

            mHasBounds = true;
        } else {
            throw new RuntimeException("Invalid bounds: " + bounds);
        }
    }

    @Override
    public String toString() {
        return mDisplayName;
    }

    public String getAttribute(String key) {
        return mAttributes.get(key);
    }

    @Override
    public Object[] getAttributesArray() {
        // this approach means we do not handle the situation where an attribute is added
        // after this function is first called. This is currently not a concern because the
        // tree is supposed to be readonly
        if (mCachedAttributesArray == null) {
            mCachedAttributesArray = new Object[mAttributes.size()];
            int i = 0;
            for (String attr : mAttributes.keySet()) {
                mCachedAttributesArray[i++] = new AttributePair(attr, mAttributes.get(attr));
            }
        }
        return mCachedAttributesArray;
    }
}