import http.server
import socketserver
import os
import urllib.parse

PORT = 8080
DIRECTORY = "/home/user/Font"

class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DIRECTORY, **kwargs)

    def do_GET(self):
        # Decode path
        parsed_path = urllib.parse.unquote(self.path)
        
        # If requesting root or index, serve a Persian download portal
        if self.path == '/' or self.path.startswith('/index.html') or self.path.startswith('/?'):
            self.send_response(200)
            self.send_header("Content-type", "text/html; charset=utf-8")
            self.end_headers()
            
            html = """<!DOCTYPE html>
<html lang="fa" dir="rtl">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>مرکز دانلود و پیش‌نمایش فایل کارنامه و مانیتورینگ نواحی</title>
    <style>
        :root {
            --primary: #1B365D;
            --accent: #2980B9;
            --success: #27AE60;
            --bg: #F4F6F9;
            --card-bg: #FFFFFF;
            --text: #2C3E50;
        }
        * { box-sizing: border-box; font-family: 'Tahoma', 'Segoe UI', sans-serif; }
        body {
            background-color: var(--bg);
            color: var(--text);
            margin: 0;
            padding: 20px;
            direction: rtl;
        }
        .container {
            max-width: 1000px;
            margin: 0 auto;
        }
        .header {
            background: linear-gradient(135deg, #1B365D, #2C3E50);
            color: white;
            padding: 30px;
            border-radius: 12px;
            text-align: center;
            box-shadow: 0 4px 15px rgba(0,0,0,0.1);
            margin-bottom: 25px;
        }
        .header h1 { margin: 0 0 10px 0; font-size: 24px; }
        .header p { margin: 0; opacity: 0.9; font-size: 14px; }
        .download-card {
            background: var(--card-bg);
            border-radius: 12px;
            padding: 25px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.06);
            margin-bottom: 20px;
            border-top: 5px solid var(--success);
        }
        .download-btn {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            background: #27AE60;
            color: white;
            padding: 14px 28px;
            font-size: 17px;
            font-weight: bold;
            border-radius: 8px;
            text-decoration: none;
            transition: all 0.2s;
            box-shadow: 0 4px 10px rgba(39, 174, 96, 0.3);
            margin: 10px 5px;
        }
        .download-btn:hover {
            background: #219150;
            transform: translateY(-2px);
            box-shadow: 0 6px 14px rgba(39, 174, 96, 0.4);
        }
        .download-btn.secondary {
            background: #2980B9;
            box-shadow: 0 4px 10px rgba(41, 128, 185, 0.3);
        }
        .download-btn.secondary:hover {
            background: #1F618D;
        }
        .download-btn.info {
            background: #34495E;
            box-shadow: 0 4px 10px rgba(52, 73, 94, 0.3);
        }
        .features {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
            gap: 15px;
            margin: 25px 0;
        }
        .feature-item {
            background: white;
            padding: 18px;
            border-radius: 8px;
            box-shadow: 0 2px 6px rgba(0,0,0,0.04);
            border-right: 4px solid var(--accent);
        }
        .feature-item h3 { margin: 0 0 8px 0; font-size: 15px; color: var(--primary); }
        .feature-item p { margin: 0; font-size: 13px; color: #555; line-height: 1.6; }
        .table-preview {
            background: white;
            border-radius: 12px;
            padding: 20px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.06);
            overflow-x: auto;
        }
        table {
            width: 100%;
            border-collapse: collapse;
            font-size: 13px;
            text-align: center;
        }
        th {
            background-color: #1B365D;
            color: white;
            padding: 10px;
            font-weight: bold;
        }
        td {
            padding: 9px;
            border-bottom: 1px solid #EAECEE;
        }
        tr:nth-child(even) { background-color: #F8FAFC; }
        .badge {
            display: inline-block;
            padding: 3px 8px;
            border-radius: 12px;
            font-size: 11px;
            font-weight: bold;
        }
        .badge-success { background: #D4EDDA; color: #155724; }
        .badge-info { background: #D1ECF1; color: #0C5460; }
        .badge-warning { background: #FFF3CD; color: #856404; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>📥 دریافت فایل اکسل کارنامه و مانیتورینگ عملکرد نواحی</h1>
            <p>نسرا استان اصفهان - سال ارزیابی ۱۴۰۵ | شیت داشبورد مانیتورینگ عملکرد با موفقیت افزوده شد</p>
        </div>

        <div class="download-card" style="text-align: center;">
            <h2 style="margin-top: 0; color: #1B365D;">برای دانلود مستقیم فایل‌ها روی دکمه‌های زیر کلیک فرمایید:</h2>
            <div style="margin: 20px 0;">
                <a href="/تهیه کارنامه نواحی.xlsx" class="download-btn" download>
                    📥 دانلود فایل اصلی: تهیه کارنامه نواحی.xlsx (شامل داشبورد مانیتورینگ)
                </a>
            </div>
            <div>
                <a href="/گزارش شهریور ماه 1405 ناحیه.xlsx" class="download-btn secondary" download>
                    📄 دانلود فرم خام: گزارش ماهانه نواحی.xlsx
                </a>
                <a href="/راهنمای_داشبورد_مانیتورینگ.md" class="download-btn info" download>
                    📖 دانلود راهنمای کامل (Markdown)
                </a>
            </div>
            <p style="font-size: 12px; color: #7F8C8D; margin-top: 15px;">
                💡 همچنین می‌توانید مستقیماً از گیت‌هاب نیز دانلود فرمایید (لینک در پیام چت درج شده است).
            </p>
        </div>

        <div class="features">
            <div class="feature-item">
                <h3>📊 شیت اول: داشبورد مانیتورینگ نواحی</h3>
                <p>پایش برخط و خودکار ۳۲ ناحیه/شهرستان، کارت‌های خلاصه شاخص‌های کلیدی، رتبه‌بندی استانی، تفکیک سطوح عالی/خوب/متوسط/ضعیف و نمودار ستونی درصد تحقق شاخص‌ها.</p>
            </div>
            <div class="feature-item">
                <h3>📋 شیت دوم: کارنامه هوشمند</h3>
                <p>امکان انتخاب هر شهرستان از منوی کشویی و صدور کارنامه تک‌برگی رسمی با نمایش اهداف، عملکرد واقعی، درصد تحقق، رتبه در استان و کلید بازگشت به داشبورد.</p>
            </div>
            <div class="feature-item">
                <h3>✏️ شیت سوم: گزارش عملکرد ماهانه</h3>
                <p>محل ورود داده‌های دریافتی از کارمندان. با وارد کردن اعداد در این شیت، تمام محاسبات، رتبه‌ها و نمودار داشبورد به صورت آنی و خودکار بروزرسانی می‌شوند.</p>
            </div>
        </div>

        <div class="table-preview">
            <h3 style="color: #1B365D; margin-top: 0;">نمای خلاصه حدانتظارهای ابلاغی به ازای هر حوزه مقاومت</h3>
            <table>
                <thead>
                    <tr>
                        <th>ردیف</th>
                        <th>عنوان شاخص عملکردی</th>
                        <th>ضریب و مبنای هر حوزه</th>
                        <th>حدانتظار کل استان اصفهان</th>
                        <th>مبدأ در گزارش ماهانه کارمند</th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td>۱</td>
                        <td><strong>سواد رسانه حضوری و توانمندسازی</strong></td>
                        <td>۳۱ جلسه به ازای حوزه</td>
                        <td>۷,۰۹۹ جلسه</td>
                        <td>مجموع شیت‌های «سوادرسانه حضوری» + «توانمندسازی گردان»</td>
                    </tr>
                    <tr>
                        <td>۲</td>
                        <td><strong>سواد رسانه آنلاین و آفلاین (مجازی)</strong></td>
                        <td>۲۱۷ برنامه به ازای حوزه</td>
                        <td>۴۹,۶۹۳ برنامه</td>
                        <td>شیت «سوادرسانه مجازی (لایو)»</td>
                    </tr>
                    <tr>
                        <td>۳</td>
                        <td><strong>اقدامات و ابتکارات خلاقانه</strong></td>
                        <td>۶۲ اقدام به ازای حوزه</td>
                        <td>۱۴,۱۹۸ اقدام</td>
                        <td>شیت «سواد رسانه خلاقانه»</td>
                    </tr>
                    <tr>
                        <td>۴</td>
                        <td><strong>تولیدات رسانه‌ای و محتوایی</strong></td>
                        <td>۳ اثر به ازای حوزه</td>
                        <td>۶۸۷ اثر</td>
                        <td>شیت «تولیدات»</td>
                    </tr>
                    <tr>
                        <td>۵</td>
                        <td><strong>نشست دبیر نسرا با انجمن مدرسان</strong></td>
                        <td>۱ نشست به ازای ناحیه</td>
                        <td>۳۲ نشست</td>
                        <td>جلسات رسمی دبیر با مدرسان شهرستان</td>
                    </tr>
                    <tr>
                        <td>۶</td>
                        <td><strong>اعضای انجمن مدرسان</strong></td>
                        <td>۲۲ نفر به ازای ناحیه</td>
                        <td>۷۰۴ مدرس</td>
                        <td>ظرفیت‌سازی شبکه مدرسان استان</td>
                    </tr>
                </tbody>
            </table>
        </div>
    </div>
</body>
</html>
"""
            self.wfile.write(html.encode('utf-8'))
            return
            
        return super().do_GET()

if __name__ == "__main__":
    with socketserver.TCPServer(("0.0.0.0", PORT), Handler) as httpd:
        print(f"Serving at http://0.0.0.0:{PORT}")
        httpd.serve_forever()
