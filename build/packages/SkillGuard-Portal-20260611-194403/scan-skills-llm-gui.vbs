Option Explicit

Dim shell, fso, root, command
Set shell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")

root = fso.GetParentFolderName(WScript.ScriptFullName)
shell.CurrentDirectory = root

command = "javaw.exe -jar " & Quote(root & "\dist\skillguard.jar") _
    & " scan " & Quote(root & "\skills") _
    & " --format html --output " & Quote(root & "\build\skillguard-ai-report.html") _
    & " --LLM"

shell.Run command, 0, False

Function Quote(value)
    Quote = Chr(34) & value & Chr(34)
End Function
