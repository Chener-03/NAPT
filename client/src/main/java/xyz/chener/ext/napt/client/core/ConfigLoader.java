package xyz.chener.ext.napt.client.core;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.Scanner;

public class ConfigLoader {

    public static class KeyEnum{
        public static final String PORT = "port";
        public static final String UID = "uid";
        public static final String HOST = "host";
        public static final String FORCE_ACCESS = "force_access";


    }


    private Properties properties = new Properties();

    public ConfigLoader(){
        try {
            properties.load(new FileInputStream("./napt_client.properties"));
        }catch (Exception e){
            inputConfig();
            try {
                properties.put(KeyEnum.FORCE_ACCESS,"false");
                properties.store(new FileOutputStream("./napt_client.properties"),"DEFAULT_CONFIG");
            } catch (IOException ignored) { }
        }
    }

    public String get(String key){
        return properties.getProperty(key);
    }

    private void inputConfig(){
        input0(KeyEnum.HOST,false);
        input0(KeyEnum.PORT,true);
        input0(KeyEnum.UID,false);
    }

    private void input0(String key,boolean isnum){
        Scanner scanner = new Scanner(System.in);
        while (true){
            System.out.printf("请输入 %s :%n",key);
            String s = scanner.nextLine();
            try {
                if (isnum){
                    if (Integer.parseInt(s) < 1 )
                        throw new Exception();
                }
                if (s != null && !s.isEmpty()){
                    properties.setProperty(key,s);
                    break;
                }
                throw new Exception();
            }catch (Exception exception){
                System.err.println("输入不合法.");
            }
        }
    }

}
