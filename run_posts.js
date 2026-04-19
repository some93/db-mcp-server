const http = require('http');

function post(path, body, label) {
  return new Promise((resolve) => {
    const bodyStr = JSON.stringify(body);
    const opts = {
      hostname: '127.0.0.1',
      port: 11198,
      path: path,
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(bodyStr)
      }
    };
    const req = http.request(opts, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        const fs = require('fs');
        fs.writeFileSync('d:/Workspace/db-mcp-server/' + label + '.json', data, 'utf8');
        resolve(data);
      });
    });
    req.on('error', (e) => {
      const fs = require('fs');
      fs.writeFileSync('d:/Workspace/db-mcp-server/' + label + '.json', JSON.stringify({error: e.message}), 'utf8');
      resolve(null);
    });
    req.write(bodyStr);
    req.end();
  });
}

async function main() {
  await post('/api/query', {datasourceName: 'root_db', sql: 'SELECT * FROM sys.user_summary LIMIT 3'}, 'post1');
  await post('/api/query', {datasourceName: 'some_db', sql: "SELECT table_name FROM information_schema.tables WHERE table_schema='ry' LIMIT 5"}, 'post2');
  console.log('done');
}

main();
