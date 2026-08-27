$out = "C:\Users\Administrator\WorkBuddy\brain\affirmation-app\_cred_out.txt"
try {
    $code = @'
using System;
using System.Runtime.InteropServices;
using System.Text;
[StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
public struct CREDENTIAL {
    public uint Flags;
    public uint Type;
    public string TargetName;
    public string Comment;
    public System.Runtime.InteropServices.ComTypes.FILETIME LastWritten;
    public uint CredentialBlobSize;
    public IntPtr CredentialBlob;
    public uint Persist;
    public uint AttributeCount;
    public IntPtr Attribute;
    public string TargetAlias;
    public string UserName;
}
public class WinCred {
    [DllImport("advapi32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    public static extern bool CredReadW(string target, uint type, int reservedFlag, out IntPtr credPtr);
    [DllImport("advapi32.dll")]
    public static extern void CredFree(IntPtr credPtr);
    public static string Get(string target, uint type) {
        IntPtr p = IntPtr.Zero;
        bool ok = CredReadW(target, type, 0, out p);
        if (!ok) return "ERR:" + Marshal.GetLastWin32Error();
        CREDENTIAL c = (CREDENTIAL)Marshal.PtrToStructure(p, typeof(CREDENTIAL));
        byte[] blob = new byte[c.CredentialBlobSize];
        Marshal.Copy(c.CredentialBlob, blob, 0, (int)c.CredentialBlobSize);
        CredFree(p);
        return c.UserName + "|" + Encoding.UTF8.GetString(blob);
    }
}
'@
    Add-Type -TypeDefinition $code
    $lines = @()
    @("git:https://github.com","git:github.com","github.com") | ForEach-Object {
        $lines += "GENERIC [$_] => " + [WinCred]::Get($_, 1)
        $lines += "WINDOWS [$_] => " + [WinCred]::Get($_, 2)
    }
    $lines | Out-File -FilePath $out -Encoding utf8
} catch {
    ("ERROR: " + $_.Exception.Message) | Out-File -FilePath $out -Encoding utf8
}
