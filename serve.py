#!/usr/bin/env python3
import http.server
import os
import urllib.parse
from datetime import datetime
import sys

class FileInfoHTTPRequestHandler(http.server.SimpleHTTPRequestHandler):
    def list_directory(self, path):
        try:
            entries = os.listdir(path)
        except OSError:
            self.send_error(404, "Cannot read directory")
            return None
        
        entries.sort(key=lambda a: (not os.path.isdir(os.path.join(path, a)), a.lower()))
        
        displaypath = urllib.parse.unquote(self.path, errors='surrogatepass')
        displaypath = displaypath.replace('\\', '/')
        
        html = f'''<!DOCTYPE HTML>
<html>
<head>
<meta charset="utf-8">
<title>Directory listing for {displaypath}</title>
<style>
body {{ font-family: Arial, sans-serif; margin: 20px; }}
table {{ border-collapse: collapse; width: 100%; }}
th, td {{ text-align: left; padding: 8px; border-bottom: 1px solid #ddd; }}
th {{ background-color: #f2f2f2; }}
tr:hover {{ background-color: #f5f5f5; }}
.file-size {{ color: #666; }}
.file-date {{ color: #888; font-size: 0.9em; }}
</style>
</head>
<body>
<h1>Directory listing for {displaypath}</h1>
<hr>
<table>
<tr>
<th>文件名</th>
<th>大小</th>
<th>修改日期</th>
</tr>
'''
        
        for name in entries:
            fullname = os.path.join(path, name)
            displayname = linkname = name
            
            try:
                stat = os.stat(fullname)
                size = stat.st_size
                mtime = datetime.fromtimestamp(stat.st_mtime)
                
                if size < 1024:
                    size_str = f"{size} B"
                elif size < 1024 * 1024:
                    size_str = f"{size / 1024:.1f} KB"
                elif size < 1024 * 1024 * 1024:
                    size_str = f"{size / (1024 * 1024):.1f} MB"
                else:
                    size_str = f"{size / (1024 * 1024 * 1024):.1f} GB"
                
                date_str = mtime.strftime("%Y-%m-%d %H:%M:%S")
            except:
                size_str = "Unknown"
                date_str = "Unknown"
            
            if os.path.isdir(fullname):
                displayname = name + "/"
                linkname = name + "/"
                size_str = "-"
            
            linkname = urllib.parse.quote(linkname, errors='surrogatepass')
            
            html += f'''<tr>
<td><a href="{linkname}">{displayname}</a></td>
<td class="file-size">{size_str}</td>
<td class="file-date">{date_str}</td>
</tr>
'''
        
        html += '''</table>
<hr>
</body>
</html>'''
        
        encoded = html.encode('utf-8', 'surrogateescape')
        self.send_response(200)
        self.send_header("Content-type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)
        return None

if __name__ == '__main__':
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8000
    directory = sys.argv[2] if len(sys.argv) > 2 else "."
    
    os.chdir(directory)
    handler = FileInfoHTTPRequestHandler
    httpd = http.server.HTTPServer(("", port), handler)
    print(f"Serving on port {port} from {os.getcwd()}")
    httpd.serve_forever()
