package org.lsposed.lspd.service;
import android.os.IBinder;
import android.os.IInterface;
import android.os.ParcelFileDescriptor;
public interface ILSPInjectedModuleService extends IInterface {
    @Override android.os.IBinder asBinder();
    ParcelFileDescriptor getContentProvider(String name);
}
