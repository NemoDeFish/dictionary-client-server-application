import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.locks.*;
import com.google.gson.Gson;

/**
 * ~ Dictionary Class ~
 * This is the class that manages the concurrent requests
 * to the dictionary.
 *
 * @author Si Yong Lim
 * Student ID: 1507003
 */
public class Dictionary {
    private final HashMap<String, HashSet<String>> dictionary;
    private final ReadWriteLock rw = new ReentrantReadWriteLock(true);
    private final Lock r = rw.readLock();
    private final Lock w = rw.writeLock();
    private final String filename;
    private final Gson gson = new Gson();
    // Synchronize file writes
    private final Object fileLock = new Object();

    public Dictionary(HashMap<String, HashSet<String>> dictionary, String filename) {
        this.dictionary = dictionary;
        this.filename = filename;
    }

    /**
     * Search word in dictionary with sleep duration delay
     * @param word word to be searched
     * @param sleepDuration time delay to sleep
     * @return HashSet of definitions for given word
     */
    public HashSet<String> search(String word, int sleepDuration) throws InterruptedException {
        // Lock read lock
        r.lock();
        try {
            // Use client's delay while holding the read lock
            Thread.sleep(sleepDuration);
            return dictionary.getOrDefault(word, null);
        } finally {
            r.unlock();
        }
    }

    /**
     * Adds word to dictionary
     * @param word word to be added
     * @param sleepDuration time delay to sleep
     * @param meaningsList list of meanings to be added
     * @return status of operation
     */
    public String add(String word, int sleepDuration, ArrayList<String> meaningsList) throws InterruptedException {
        // Lock write lock
        w.lock();
        try {
            // Use client's delay while holding the read lock
            Thread.sleep(sleepDuration);
            if (!dictionary.containsKey(word)) {
                HashSet<String> meaningsSet = new HashSet<>(meaningsList);

                // Check whether client has provided duplicate meanings
                if (meaningsSet.size() < meaningsList.size()) {
                    return "Duplicate meanings";
                } else {
                    dictionary.put(word, meaningsSet);
                    saveToFile();
                    return "Success";
                }
            } else {
                return null;
            }
        } finally {
            w.unlock();
        }
    }

    /**
     * Remove word from dictionary
     * @param word word to be removed
     * @param sleepDuration time delay to sleep
     * @return status of operation
     */
    public String remove(String word, int sleepDuration) throws InterruptedException {
        // Lock write lock
        w.lock();
        try {
            // Use client's delay while holding the read lock
            Thread.sleep(sleepDuration);
            if (dictionary.containsKey(word)) {
                dictionary.remove(word);
                saveToFile();
                return "Success";
            } else {
                return null;
            }
        } finally {
            w.unlock();
        }
    }

    /**
     * Add a new meaning to word in dictionary
     * @param word word to be added to
     * @param sleepDuration time delay to sleep
     * @param newMeaning new meaning to be added
     * @return status of operation
     */
    public String addMeaning(String word, int sleepDuration, String newMeaning) throws InterruptedException {
        // Lock write lock
        w.lock();
        try {
            // Use client's delay while holding the read lock
            Thread.sleep(sleepDuration);
            if (dictionary.containsKey(word)) {
                HashSet<String> existingMeanings = dictionary.get(word);
                if (existingMeanings.contains(newMeaning)) {
                    return "Duplicate";
                } else {
                    existingMeanings.add(newMeaning);
                    dictionary.replace(word, existingMeanings);
                    saveToFile();
                    return "Success";
                }
            } else {
                return null;
            }
        } finally {
            w.unlock();
        }
    }

    /**
     * Update meaning to word in dictionary
     * @param word word to be updated
     * @param sleepDuration time delay to sleep
     * @param existingMeaning existing meaning to be modified
     * @param newMeaning new meaning to be added
     * @return status of operation
     */
    public String update(String word, int sleepDuration, String existingMeaning, String newMeaning) throws InterruptedException {
        // Lock write lock
        w.lock();
        try {
            // Use client's delay while holding the read lock
            Thread.sleep(sleepDuration);
            if (dictionary.containsKey(word)) {
                HashSet<String> existingMeanings = dictionary.get(word);
                // Check if existing meaning already exists in dictionary
                if (existingMeanings.contains(existingMeaning)) {
                    // Check if new meaning already exists in dictionary
                    if (!existingMeanings.contains(newMeaning)) {
                        existingMeanings.remove(existingMeaning);
                        existingMeanings.add(newMeaning);
                        dictionary.replace(word, existingMeanings);
                        saveToFile();
                        return "Success";
                    } else {
                        return "Duplicate new meaning";
                    }
                } else {
                    return "Meaning not found";
                }
            } else {
                return null;
            }
        } finally {
            w.unlock();
        }
    }

    /**
     * Saves dictionary to file
     */
    public void saveToFile() {
        // Lock read lock because we are reading dictionary
        r.lock();
        try {
            // Lock when writing to writing
            synchronized (fileLock) {
                DictionaryServerGUI.displayResult("SERVER: Writing to file");
                // Write to temporary file in case the write gets truncated midway
                File tempFile = new File(filename + ".tmp");
                File finalFile = new File(filename);

                try (Writer writer = new FileWriter(tempFile)) {
                    gson.toJson(this.dictionary, writer);
                    writer.flush();
                } catch (IOException e) {
                    DictionaryServerGUI.displayResult("SERVER: " + e.getMessage());
                }

                // Replace original file atomically only if temp is written successfully
                Path sourcePath = tempFile.toPath();
                Path targetPath = finalFile.toPath();
                Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
        } catch (IOException e) {
            DictionaryServerGUI.displayResult("SERVER: " + e.getMessage());
        } finally {
            DictionaryServerGUI.displayResult("SERVER: Written to file");
            r.unlock();
        }
    }
}
