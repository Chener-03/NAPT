package xyz.chener.ext.napt.client;

import xyz.chener.ext.napt.client.core.ClientCore;
import xyz.chener.ext.napt.client.core.ConfigLoader;
import xyz.chener.ext.napt.client.core.Continer;

public class Main {
    public static void main(String[] args)  {
        Continer.put(ConfigLoader.class,new ConfigLoader());
        Continer.put(ClientCore.class,new ClientCore());
    }
}