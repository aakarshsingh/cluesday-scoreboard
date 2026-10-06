import { defineRailway, github, preserve, project, service } from "railway/iac";

// This repository manages only its own resources in the environment. Other
// repositories export their own partial name.
// See https://docs.railway.com/infrastructure-as-code#multi-repo-projects
export const partial = "cluesday-scoreboard";

export default defineRailway(() => {
  const cluesday_scoreboard = service("cluesday-scoreboard", {
    source: github("aakarshsingh/cluesday-scoreboard", { branch: "main" }),
    build: { builder: "DOCKERFILE", dockerfilePath: "Dockerfile" },
    start: "java -jar app.jar",
    env: {
      // Postgres is a separate Railway service; reference its variables.
      PGHOST: "${{Postgres.PGHOST}}",
      PGPORT: "${{Postgres.PGPORT}}",
      PGDATABASE: "${{Postgres.PGDATABASE}}",
      PGUSER: "${{Postgres.PGUSER}}",
      PGPASSWORD: "${{Postgres.PGPASSWORD}}",
      // Secrets set in the dashboard — keep whatever value is there.
      ADMIN_USER: preserve(),
      ADMIN_PASS: preserve(),
    },
  });
  return project("cluesday-scoreboard", {
    resources: [cluesday_scoreboard],
  });
});
