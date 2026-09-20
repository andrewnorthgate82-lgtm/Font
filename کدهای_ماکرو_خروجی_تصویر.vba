Attribute VB_Name = "Module_ExportImages"
' ====================================================================
'  کدهای ماکرو جهت دریافت خروجی تصویر از کارنامه و داشبورد مانیتورینگ
'  نهضت سواد رسانه‌ای انقلاب اسلامی (نسرا) - استان اصفهان
'  پشتیبانی از تفکیک ماه ارزیابی (شهریور، مهر، ...)
' ====================================================================

''' <summary>
''' ۱. خروجی تصویر کارنامه شهرستان جاری در دسکتاپ
''' </summary>
Sub ExportScorecardAsImage()
    Dim ws As Worksheet
    Dim rng As Range
    Dim chtObj As ChartObject
    Dim filePath As String
    Dim districtName As String
    Dim monthName As String
    Dim desktopPath As String
    
    On Error GoTo ErrorHandler
    
    Set ws = ThisWorkbook.Sheets("کارنامه هوشمند")
    
    districtName = Trim(ws.Range("C2").Value)
    If districtName = "" Then districtName = "ناحیه"
    
    monthName = Trim(ws.Range("C3").Value)
    If monthName = "" Then monthName = "ماهانه"
    
    ' محدوده اصلی کارنامه هوشمند (شامل نام شهرستان، ماه و جدول شاخص‌ها)
    Set rng = ws.Range("B1:E16")
    
    desktopPath = Environ("USERPROFILE") & "\Desktop\"
    filePath = desktopPath & "کارنامه_" & districtName & "_" & monthName & ".png"
    
    rng.CopyPicture Appearance:=xlScreen, Format:=xlPicture
    DoEvents
    
    Set chtObj = ws.ChartObjects.Add(Left:=rng.Left, Top:=rng.Top, Width:=rng.Width, Height:=rng.Height)
    chtObj.ShapeRange.Line.Visible = msoFalse
    chtObj.Activate
    
    ActiveChart.Paste
    DoEvents
    ActiveChart.Export Filename:=filePath, FilterName:="PNG"
    chtObj.Delete
    
    MsgBox "تصویر کارنامه شهرستان «" & districtName & "» برای ماه «" & monthName & "» با موفقیت در دسکتاپ ذخیره شد:" & vbCrLf & filePath, vbInformation, "خروجی موفق کارنامه"
    Exit Sub

ErrorHandler:
    If Not chtObj Is Nothing Then chtObj.Delete
    MsgBox "خطا در استخراج تصویر: " & Err.Description, vbCritical, "خطا"
End Sub


''' <summary>
''' ۲. خروجی تصویر داشبورد مانیتورینگ استان در دسکتاپ (جهت ارسال برای مسئول)
''' </summary>
Sub ExportDashboardAsImage()
    Dim ws As Worksheet, wsK As Worksheet
    Dim rng As Range
    Dim chtObj As ChartObject
    Dim filePath As String
    Dim monthName As String
    Dim desktopPath As String
    
    On Error GoTo ErrorHandler
    
    Set ws = ThisWorkbook.Sheets("داشبورد مانیتورینگ نواحی")
    Set wsK = ThisWorkbook.Sheets("کارنامه هوشمند")
    
    monthName = Trim(wsK.Range("C3").Value)
    If monthName = "" Then monthName = "ماهانه"
    
    Set rng = ws.Range("A1:W26")
    
    desktopPath = Environ("USERPROFILE") & "\Desktop\"
    filePath = desktopPath & "داشبورد_مدیریتی_مانیتورینگ_استان_اصفهان_" & monthName & ".png"
    
    rng.CopyPicture Appearance:=xlScreen, Format:=xlPicture
    DoEvents
    
    Set chtObj = ws.ChartObjects.Add(Left:=rng.Left, Top:=rng.Top, Width:=rng.Width, Height:=rng.Height)
    chtObj.ShapeRange.Line.Visible = msoFalse
    chtObj.Activate
    
    ActiveChart.Paste
    DoEvents
    ActiveChart.Export Filename:=filePath, FilterName:="PNG"
    chtObj.Delete
    
    MsgBox "تصویر داشبورد مدیریتی استان اصفهان (ماه " & monthName & ") با موفقیت در دسکتاپ ذخیره شد:" & vbCrLf & filePath, vbInformation, "خروجی موفق داشبورد"
    Exit Sub

ErrorHandler:
    If Not chtObj Is Nothing Then chtObj.Delete
    MsgBox "خطا در استخراج تصویر داشبورد: " & Err.Description, vbCritical, "خطا"
End Sub


''' <summary>
''' ۳. خروجی گروهی تصویر تمام ۳۲ شهرستان در یک پوشه روی دسکتاپ (فوق‌العاده سریع!)
''' </summary>
Sub ExportAll32Scorecards()
    Dim wsK As Worksheet, wsDB As Worksheet
    Dim rng As Range
    Dim chtObj As ChartObject
    Dim folderPath As String
    Dim districtName As String
    Dim monthName As String
    Dim i As Long
    Dim countSuccess As Long
    
    On Error GoTo ErrorHandler
    
    Set wsK = ThisWorkbook.Sheets("کارنامه هوشمند")
    Set wsDB = ThisWorkbook.Sheets("پایگاه داده حد انتظار")
    
    monthName = Trim(wsK.Range("C3").Value)
    If monthName = "" Then monthName = "ماهانه"
    
    folderPath = Environ("USERPROFILE") & "\Desktop\کارنامه‌های_نواحی_نسرا_" & monthName & "\"
    If Dir(folderPath, vbDirectory) = "" Then
        MkDir folderPath
    End If
    
    Application.ScreenUpdating = False
    countSuccess = 0
    
    For i = 2 To 33
        districtName = wsDB.Cells(i, 1).Value
        If districtName <> "" Then
            wsK.Range("C2").Value = districtName
            Calculate
            DoEvents
            
            Set rng = wsK.Range("B1:E16")
            rng.CopyPicture Appearance:=xlScreen, Format:=xlPicture
            DoEvents
            
            Set chtObj = wsK.ChartObjects.Add(Left:=rng.Left, Top:=rng.Top, Width:=rng.Width, Height:=rng.Height)
            chtObj.ShapeRange.Line.Visible = msoFalse
            chtObj.Activate
            ActiveChart.Paste
            DoEvents
            ActiveChart.Export Filename:=folderPath & "کارنامه_" & districtName & ".png", FilterName:="PNG"
            chtObj.Delete
            
            countSuccess = countSuccess + 1
        End If
    Next i
    
    Application.ScreenUpdating = True
    MsgBox "عملیات با موفقیت پایان یافت!" & vbCrLf & _
           "تعداد " & countSuccess & " تصویر کارنامه مربوط به ماه «" & monthName & "» در پوشه زیر در دسکتاپ ذخیره شد:" & vbCrLf & _
           folderPath, vbInformation, "استخراج گروهی موفق"
    Exit Sub

ErrorHandler:
    Application.ScreenUpdating = True
    If Not chtObj Is Nothing Then chtObj.Delete
    MsgBox "خطا در خروجی گروهی: " & Err.Description, vbCritical, "خطا"
End Sub
