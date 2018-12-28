package cn.openread;

import org.apache.catalina.startup.Bootstrap;

public class SimpleTest {

    public static void main(String args[]) throws Exception {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.start();
        Thread.sleep(Integer.MAX_VALUE);
    }
}
