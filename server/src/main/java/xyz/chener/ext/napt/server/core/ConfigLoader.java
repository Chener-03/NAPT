package xyz.chener.ext.napt.server.core;

import java.io.FileInputStream;
import java.util.Properties;

public class ConfigLoader {

    public static class KeyEnum{
        public static final String PORT = "port";
        public static final String WEBPORT = "webport";
        public static final String U = "u";
        public static final String P = "p";
    }


    private Properties properties = new Properties();

    public ConfigLoader(){
        try {
            properties.load(new FileInputStream("./proxy.properties"));
        }catch (Exception e){
            properties.put("port","4901");
            properties.put("webport","4900");
            properties.put("u","admin");
            properties.put("p","admin");
        }
    }

    public String get(String key){
        return properties.getProperty(key);
    }

}
