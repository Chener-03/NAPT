package xyz.chener.ext.napt.server.core;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
            properties.load(new FileInputStream("./napt_server.properties"));
        }catch (Exception e){
            properties.put("port","4901");
            properties.put("webport","4900");
            properties.put("u","admin");
            properties.put("p","admin");
            try {
                properties.store(new FileOutputStream("./napt_server.properties"),"默认配置");
            } catch (IOException ignored) { }
        }
    }

    public String get(String key){
        return properties.getProperty(key);
    }

}
