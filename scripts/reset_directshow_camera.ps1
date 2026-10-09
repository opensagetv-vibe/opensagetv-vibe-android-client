param(
    [Parameter(Mandatory = $false)]
    [string] $Camera = 'HD Pro Webcam C920'
)

$ErrorActionPreference = 'Stop'

if (-not ('Vibe.DirectShowCameraReset' -as [type])) {
    Add-Type -Language CSharp -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Runtime.InteropServices.ComTypes;

namespace Vibe
{
    [Flags]
    public enum CameraControlFlags
    {
        None = 0,
        Auto = 1,
        Manual = 2
    }

    public enum CameraControlProperty
    {
        Pan = 0,
        Tilt = 1,
        Roll = 2,
        Zoom = 3,
        Exposure = 4,
        Iris = 5,
        Focus = 6
    }

    [ComImport]
    [Guid("29840822-5B84-11D0-BD3B-00A0C911CE86")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface ICreateDevEnum
    {
        [PreserveSig]
        int CreateClassEnumerator(
            [In] ref Guid category,
            [Out] out IEnumMoniker enumMoniker,
            int flags);
    }

    [ComImport]
    [Guid("55272A00-42CB-11CE-8135-00AA004BB851")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface IPropertyBag
    {
        [PreserveSig]
        int Read(
            [MarshalAs(UnmanagedType.LPWStr)] string propertyName,
            [Out, MarshalAs(UnmanagedType.Struct)] out object value,
            IntPtr errorLog);

        [PreserveSig]
        int Write(
            [MarshalAs(UnmanagedType.LPWStr)] string propertyName,
            [In, MarshalAs(UnmanagedType.Struct)] ref object value);
    }

    [ComImport]
    [Guid("C6E13370-30AC-11D0-A18C-00A0C9118956")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface IAMCameraControl
    {
        [PreserveSig]
        int GetRange(
            CameraControlProperty property,
            out int minimum,
            out int maximum,
            out int step,
            out int defaultValue,
            out CameraControlFlags capabilities);

        [PreserveSig]
        int Set(
            CameraControlProperty property,
            int value,
            CameraControlFlags flags);

        [PreserveSig]
        int Get(
            CameraControlProperty property,
            out int value,
            out CameraControlFlags flags);
    }

    public sealed class CameraResetResult
    {
        public string Camera { get; set; }
        public bool Found { get; set; }
        public List<CameraPropertyResult> Properties { get; set; }

        public CameraResetResult()
        {
            Properties = new List<CameraPropertyResult>();
        }
    }

    public sealed class CameraPropertyResult
    {
        public string Name { get; set; }
        public bool Supported { get; set; }
        public int Before { get; set; }
        public int Default { get; set; }
        public int After { get; set; }
        public string BeforeMode { get; set; }
        public string AppliedMode { get; set; }
        public string AfterMode { get; set; }
        public int Result { get; set; }
    }

    public static class DirectShowCameraReset
    {
        private static readonly Guid SystemDeviceEnum =
            new Guid("62BE5D10-60EB-11D0-BD3B-00A0C911CE86");
        private static readonly Guid VideoInputDeviceCategory =
            new Guid("860BB310-5D01-11D0-BD3B-00A0C911CE86");
        private static readonly Guid BaseFilter =
            new Guid("56A86895-0AD4-11CE-B03A-0020AF0BA770");

        public static CameraResetResult Reset(string requestedName)
        {
            var result = new CameraResetResult { Camera = requestedName };
            object enumObject = null;
            IEnumMoniker devices = null;
            try
            {
                Type type = Type.GetTypeFromCLSID(SystemDeviceEnum, true);
                enumObject = Activator.CreateInstance(type);
                var create = (ICreateDevEnum)enumObject;
                Guid category = VideoInputDeviceCategory;
                int hr = create.CreateClassEnumerator(ref category, out devices, 0);
                if (hr != 0 || devices == null) return result;

                var item = new IMoniker[1];
                while (devices.Next(1, item, IntPtr.Zero) == 0)
                {
                    IMoniker moniker = item[0];
                    object bagObject = null;
                    object filterObject = null;
                    try
                    {
                        Guid bagId = typeof(IPropertyBag).GUID;
                        moniker.BindToStorage(null, null, ref bagId, out bagObject);
                        object friendlyObject;
                        string friendly = "";
                        if (((IPropertyBag)bagObject).Read(
                                "FriendlyName", out friendlyObject, IntPtr.Zero) == 0)
                            friendly = Convert.ToString(friendlyObject) ?? "";
                        if (!string.Equals(friendly, requestedName,
                                StringComparison.OrdinalIgnoreCase))
                            continue;

                        result.Found = true;
                        Guid filterId = BaseFilter;
                        moniker.BindToObject(null, null, ref filterId, out filterObject);
                        var control = (IAMCameraControl)filterObject;
                        ResetOne(control, CameraControlProperty.Zoom, result);
                        ResetOne(control, CameraControlProperty.Pan, result);
                        ResetOne(control, CameraControlProperty.Tilt, result);
                        return result;
                    }
                    finally
                    {
                        if (filterObject != null && Marshal.IsComObject(filterObject))
                            Marshal.FinalReleaseComObject(filterObject);
                        if (bagObject != null && Marshal.IsComObject(bagObject))
                            Marshal.FinalReleaseComObject(bagObject);
                        if (moniker != null && Marshal.IsComObject(moniker))
                            Marshal.FinalReleaseComObject(moniker);
                    }
                }
                return result;
            }
            finally
            {
                if (devices != null && Marshal.IsComObject(devices))
                    Marshal.FinalReleaseComObject(devices);
                if (enumObject != null && Marshal.IsComObject(enumObject))
                    Marshal.FinalReleaseComObject(enumObject);
            }
        }

        private static void ResetOne(IAMCameraControl control,
                CameraControlProperty property, CameraResetResult result)
        {
            var row = new CameraPropertyResult { Name = property.ToString() };
            result.Properties.Add(row);
            int minimum, maximum, step, defaultValue;
            CameraControlFlags capabilities;
            int rangeHr = control.GetRange(property, out minimum, out maximum,
                out step, out defaultValue, out capabilities);
            if (rangeHr != 0)
            {
                row.Result = rangeHr;
                return;
            }
            row.Supported = true;
            row.Default = defaultValue;
            CameraControlFlags beforeMode;
            int beforeValue;
            int beforeHr = control.Get(property, out beforeValue, out beforeMode);
            row.Before = beforeHr == 0 ? beforeValue : Int32.MinValue;
            row.BeforeMode = beforeMode.ToString();

            CameraControlFlags applyMode =
                (capabilities & CameraControlFlags.Manual) != 0
                ? CameraControlFlags.Manual
                : (capabilities & CameraControlFlags.Auto) != 0
                ? CameraControlFlags.Auto
                : CameraControlFlags.None;
            row.AppliedMode = applyMode.ToString();
            row.Result = control.Set(property, defaultValue, applyMode);

            CameraControlFlags afterMode;
            int afterValue;
            int afterHr = control.Get(property, out afterValue, out afterMode);
            row.After = afterHr == 0 ? afterValue : Int32.MinValue;
            row.AfterMode = afterMode.ToString();
        }
    }
}
'@
}

$result = [Vibe.DirectShowCameraReset]::Reset($Camera)
if (-not $result.Found) {
    throw "DirectShow camera was not found: $Camera"
}
$zoom = $result.Properties | Where-Object Name -eq 'Zoom'
if (-not $zoom -or -not $zoom.Supported -or $zoom.Result -ne 0 -or
        $zoom.After -ne $zoom.Default) {
    throw "C920 zoom could not be reset to its driver default"
}
$result | ConvertTo-Json -Depth 5 -Compress
