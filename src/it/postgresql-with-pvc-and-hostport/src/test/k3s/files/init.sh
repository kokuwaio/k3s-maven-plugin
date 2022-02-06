#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    CREATE DATABASE "data";
    CREATE USER "data_owner" WITH ENCRYPTED PASSWORD 'changeMe';
    GRANT ALL PRIVILEGES ON DATABASE "data" TO "data_owner";
EOSQL
