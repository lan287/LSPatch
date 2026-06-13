package org.lsposed.lspatch.share;
public class LSPConfig {
    public static final LSPConfig instance;
    public int API_CODE = 93;
    public int VERSION_CODE = 348;
    public String VERSION_NAME = "0.6";
    public int CORE_VERSION_CODE = 93;
    public String CORE_VERSION_NAME = "1.0.3";
    static { instance = new LSPConfig(); }
}
