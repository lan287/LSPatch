package org.lsposed.lspd.service;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import org.lsposed.lspd.models.Module;
import java.util.List;
public interface ILSPApplicationService extends IInterface {
    List<Module> getLegacyModulesList() throws android.os.RemoteException;
    List<Module> getModulesList() throws android.os.RemoteException;
    String getPrefsPath(String packageName) throws android.os.RemoteException;
    ParcelFileDescriptor requestInjectedManagerBinder(List<IBinder> binder) throws android.os.RemoteException;
    public static abstract class Stub extends android.os.Binder implements ILSPApplicationService {
        private static final java.lang.String DESCRIPTOR = "org.lsposed.lspd.service.ILSPApplicationService";
        public Stub() { attachInterface(this, DESCRIPTOR); }
        public static ILSPApplicationService asInterface(android.os.IBinder obj) {
            if ((obj==null)) return null;
            android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (((iin!=null) && (iin instanceof ILSPApplicationService))) return ((ILSPApplicationService)iin);
            return new ILSPApplicationService.Stub.Proxy(obj);
        }
        @Override public android.os.IBinder asBinder() { return this; }
        @Override protected boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException {
            return super.onTransact(code, data, reply, flags);
        }
        private static class Proxy implements ILSPApplicationService {
            private android.os.IBinder mRemote;
            Proxy(android.os.IBinder remote) { mRemote = remote; }
            @Override public android.os.IBinder asBinder() { return mRemote; }
            public java.lang.String getInterfaceDescriptor() { return DESCRIPTOR; }
            @Override public List<Module> getLegacyModulesList() throws android.os.RemoteException { return null; }
            @Override public List<Module> getModulesList() throws android.os.RemoteException { return null; }
            @Override public String getPrefsPath(String packageName) throws android.os.RemoteException { return null; }
            @Override public ParcelFileDescriptor requestInjectedManagerBinder(List<IBinder> binder) throws android.os.RemoteException { return null; }
        }
    }
}
