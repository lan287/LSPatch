package org.lsposed.lspatch.share;

public class LSPConfig {
    public static final LSPConfig instance;
    public int API_CODE;
    public int VERSION_CODE;
    public String VERSION_NAME;
    public int CORE_VERSION_CODE;
    public String CORE_VERSION_NAME;
    
    private LSPConfig() {}
    
    static {
        instance = new LSPConfig();
        instance.API_CODE = 93;
        instance.VERSION_CODE = 515;
        instance.VERSION_NAME = "0.6";
        instance.CORE_VERSION_CODE = 6500;
        instance.CORE_VERSION_NAME = "1.0";
    }
}
