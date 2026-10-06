# Custom hub world (do not upload huge worlds to GitHub)

Put your hub map here **before** importing:

```
import-worlds/hub/
  level.dat
  region/
  ... (normal Minecraft world files)
```

The folder name on disk must be **`hub`** when the server runs. This repo folder is only a staging area.

## Windows

1. Copy your hub world files into `import-worlds\hub\` (merge with this README or replace contents except keep `level.dat` + `region/`).
2. Run:

   ```bat
   launch\import-hub.bat
   ```

3. Restart the server.

## Manual copy

Copy everything into:

```
server-26.2\hub\
```

(`server-26.2` is created by `python launch\setup.py` and is not in GitHub.)

After import, stand at hub spawn in-game and run **`/sethub`** (op) once.
