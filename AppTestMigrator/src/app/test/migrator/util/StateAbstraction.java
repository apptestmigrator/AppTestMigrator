package app.test.migrator.util;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Hashtable;

import app.test.migrator.uiautomator.UiNode;

public class StateAbstraction {
	
	public Hashtable<String, Integer> computeFeatureVector(UiNode root) {
		 Hashtable<String, Integer> map = new Hashtable<String, Integer>();
		 Hashtable<String, Integer> dict = new Hashtable<String, Integer>();
		 
	     computeFeatureVectorExactMatch(dict, map, root, 0);
	     
	     return map;
	}

	private void computeFeatureVectorExactMatch(Hashtable<String, Integer> dict, Hashtable<String, Integer> map, UiNode root, int level) {
		if (root == null || root.getClass() == null) {
           return;
       }

       String type = root.getAttribute("class");
    	   
       String key = type + "@" + level;

       int count = dict.containsKey(key) ? dict.get(key) : 0;
       count++;
       dict.put(key, count);

       String keyExactMatch = type + "@" + level + "@" + count + "@" + getTextDigest(root.getAttribute("text"));     
       map.put(keyExactMatch, 1);

       int child_cnt = root.getChildCount();
       if (child_cnt > 0) {
           for (int i = 0; i < child_cnt; i++) {
               UiNode child = (UiNode)root.getChildren()[i];
               computeFeatureVectorExactMatch(dict, map, child, level + 1);
           }
       }		
	}
	
	private String getTextDigest(CharSequence text) {
       MessageDigest m = null;

       try {
           m = MessageDigest.getInstance("MD5");
       } catch (NoSuchAlgorithmException e) {
           /* ignore*/
       }

       if (m == null) {
           return "";
       }

       String plaintext = "";

       if (text != null) {
           plaintext = text.toString();
       }

       m.reset();
       m.update(plaintext.getBytes());
       byte[] digest = m.digest();
       BigInteger bigInt = new BigInteger(1, digest);
       String hashtext = bigInt.toString(16);

       // Now we need to zero pad it if you actually want the full 32 chars.
       while (hashtext.length() < 32) {
           hashtext = "0" + hashtext;
       }

       return HashIdDictionary.add(hashtext);
   }

}
