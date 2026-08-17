const pool = require('./src/db/pool');

async function main() {
  const res = await pool.query("SELECT table_name, column_name, data_type FROM information_schema.columns WHERE table_schema = 'public'");
  console.log(res.rows);
  process.exit(0);
}
main().catch(console.error);
