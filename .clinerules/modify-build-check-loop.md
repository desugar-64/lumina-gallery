**MODIFY-BUILD-CHECK LOOP:** Always build immediately after code changes.

**PROCESS:**
1. **Modify** code as requested
2. **Build** project immediately 
3. **If success** → continue with task
4. **If failure** → analyze error, fix code, rebuild
5. **Repeat** until success OR 20 retries max

**FAILURE HANDLING:** Carefully analyze error messages, identify root cause, apply targeted fix. Never skip the build step.