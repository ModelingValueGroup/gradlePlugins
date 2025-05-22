//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//  (C) Copyright 2018-2025 Modeling Value Group B.V. (http://modelingvalue.org)                                         ~
//                                                                                                                       ~
//  Licensed under the GNU Lesser General Public License v3.0 (the 'License'). You may not use this file except in       ~
//  compliance with the License. You may obtain a copy of the License at: https://choosealicense.com/licenses/lgpl-3.0   ~
//  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on  ~
//  an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the   ~
//  specific language governing permissions and limitations under the License.                                           ~
//                                                                                                                       ~
//  Maintainers:                                                                                                         ~
//      Wim Bast, Tom Brus                                                                                               ~
//                                                                                                                       ~
//  Contributors:                                                                                                        ~
//      Ronald Krijgsheld ✝, Arjan Kok, Carel Bast                                                                       ~
// --------------------------------------------------------------------------------------------------------------------- ~
//  In Memory of Ronald Krijgsheld, 1972 - 2023                                                                          ~
//      Ronald was suddenly and unexpectedly taken from us. He was not only our long-term colleague and team member      ~
//      but also our friend. "He will live on in many of the lines of code you see below."                               ~
//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

package org.modelingvalue.gradle.mvgplugin;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class BashRunner {
    private final Path           scriptFile;
    private final Process        process;
    private final Sucker         inSucker;
    private final Sucker         errSucker;
    private final BufferedWriter out;
    private final List<String>   stdout = new ArrayList<>();
    private final List<String>   stderr = new ArrayList<>();

    public BashRunner(Path scriptFile) throws IOException {
        process = new ProcessBuilder("bash", scriptFile.toAbsolutePath().toString()).start();
        this.scriptFile = scriptFile;
        inSucker = new Sucker("in", new BufferedReader(new InputStreamReader(process.getInputStream())), this::handleStdinLine);
        errSucker = new Sucker("err", new BufferedReader(new InputStreamReader(process.getErrorStream())), this::handleStderrLine);
        out = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
    }

    public BashRunner waitForExit() {
        try {
            process.onExit().get();
            inSucker.join();
            errSucker.join();
            return this;
        } catch (InterruptedException | ExecutionException e) {
            throw new Error("bash script " + scriptFile + " returned abnormally", e);
        }
    }

    public int exitValue() {
        return process.exitValue();
    }

    public void tell(String line) throws IOException {
        out.write(line);
        out.newLine();
        out.flush();
    }

    public List<String> getStdout() {
        synchronized (stdout) {
            return new ArrayList<>(stdout);
        }
    }

    public List<String> getStderr() {
        synchronized (stderr) {
            return new ArrayList<>(stderr);
        }
    }

    private void handleStdinLine(String line) {
        synchronized (stdout) {
            stdout.add(line);
        }
    }

    private void handleStderrLine(String line) {
        synchronized (stderr) {
            stderr.add(line);
        }
    }

    private static class Sucker extends Thread {
        private final BufferedReader   reader;
        private final Consumer<String> action;
        private       Throwable        throwable;

        public Sucker(String name, BufferedReader reader, Consumer<String> action) {
            super("peerSucker-" + name);
            this.reader = reader;
            this.action = action;
            setDaemon(true);
            start();
        }

        @Override
        public void run() {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    action.accept(line);
                }
            } catch (IOException e) {
                throwable = e;
            }
        }

        public Throwable getThrowable() {
            return throwable;
        }
    }
}
