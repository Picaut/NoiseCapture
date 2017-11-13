/*
 * This file is part of the NoiseCapture application and OnoMap system.
 *
 * The 'OnoMaP' system is led by Lab-STICC and Ifsttar and generates noise maps via
 * citizen-contributed noise data.
 *
 * This application is co-funded by the ENERGIC-OD Project (European Network for
 * Redistributing Geospatial Information to user Communities - Open Data). ENERGIC-OD
 * (http://www.energic-od.eu/) is partially funded under the ICT Policy Support Programme (ICT
 * PSP) as part of the Competitiveness and Innovation Framework Programme by the European
 * Community. The application work is also supported by the French geographic portal GEOPAL of the
 * Pays de la Loire region (http://www.geopal.org).
 *
 * Copyright (C) IFSTTAR - LAE and Lab-STICC – CNRS UMR 6285 Equipe DECIDE Vannes
 *
 * NoiseCapture is a free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of
 * the License, or(at your option) any later version. NoiseCapture is distributed in the hope that
 * it will be useful,but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.You should have received a copy of the GNU General Public License along with this
 * program; if not, write to the Free Software Foundation,Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA 02110-1301  USA or see For more information,  write to Ifsttar,
 * 14-20 Boulevard Newton Cite Descartes, Champs sur Marne F-77447 Marne la Vallee Cedex 2 FRANCE
 *  or write to scientific.computing@ifsttar.fr
 */
package org.noise_planet.acousticmodem;

import org.noise_planet.acousticmodem.reedsolomon.ReedSolomon;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.zip.CRC32;

/**
 * Acoustic modem.
 */
public class AcousticModem {
    private Settings settings;

    // ReedSolomon parameters
    public static final int DATA_SHARDS = 4;
    public static final int PARITY_SHARDS = 2;
    public static final int CRC_SIZE = 4;

    private Integer lastInputWord = null;
    private Integer ignoreDuplicateWord = Integer.MAX_VALUE;

    public AcousticModem(Settings settings) {
        this.settings = settings;
    }


    public Settings getSettings() {
        return settings;
    }

    private void copyTone(int wordId, short[] out, int outIndex, short toneRms) {
        //TODO maybe apply a windowing function
        int freqA = settings.words[wordId][0];
        int freqB = settings.words[wordId][1];
        for (int s = 0; s < settings.wordLength; s++) {
            double t = s * (1 / (double) settings.samplingRate);
            out[outIndex + s] = (short) (Math.sin(2 * Math.PI * freqA * t) * (toneRms) + Math.sin(2 * Math.PI * freqB * t) * (toneRms));
        }
    }

    public byte[] encode(byte[] in) throws IOException {
        final int shardSize = (int)Math.ceil((in.length + settings.crcSize) / TOTAL_SHARDS);
        final int dataShardSize = shardSize - settings.crcSize;
        byte [] [] shards = new byte [TOTAL_SHARDS] [shardSize];


        for (int i = 0; i < DATA_SHARDS; i++) {
            System.arraycopy(in, i * dataShardSize, shards[i], 0, Math.min(dataShardSize, in.length - i * dataShardSize));
            // Add crc

        }

        // Use Reed-Solomon to calculate the parity.
        ReedSolomon reedSolomon = new ReedSolomon(DATA_SHARDS, PARITY_SHARDS);
        reedSolomon.encodeParity(shards, 0, shardSize);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        for (int i = 0; i < DATA_SHARDS; i++) {
            byteArrayOutputStream.write(shards[i]);
        }
        return byteArrayOutputStream.toByteArray();
    }

    public byte[] decode(byte[] in) {
        final int shardSize = (int)Math.ceil(in.length / TOTAL_SHARDS);
        byte [] [] shards = new byte [TOTAL_SHARDS] [shardSize];
        final boolean [] shardPresent = new boolean [TOTAL_SHARDS];

        for (int i = 0; i < DATA_SHARDS; i++) {
            System.arraycopy(in, i * shardSize, shards[i], 0, Math.min(shardSize, in.length - i * shardSize));
        }

        // Crc check of shard

        // Use Reed-Solomon to fill in the missing shards
        ReedSolomon reedSolomon = new ReedSolomon(DATA_SHARDS, PARITY_SHARDS);
        reedSolomon.decodeMissing(shards, shardPresent, 0, shardSize);



    }

    public static int[] byteToWordsIndex(byte data) {
        int v = data & 0xFF;
        return new int[] {v >>> 4, v & 0x0F};
    }

    public static byte wordsToByte(int wordA, int wordB) {
        return (byte)(wordA << 4 | wordB & 0x0F);
    }

    /**
     * Compute size of signal length for the provided data
     *
     * @param source       Source data
     * @param sourceIndex  First location to read in source data
     * @param sourceLength Read this size from source data
     * @return size of signal length for the provided data
     */
    public int getSignalLength(byte[] source, int sourceIndex, int sourceLength) {
        int lastWord = 0;
        int bufferLength = 0;
        for (int idByte = sourceIndex; idByte < sourceIndex + sourceLength; idByte++) {
            int[] words = byteToWordsIndex(source[idByte]);
            if (idByte > 0 && lastWord == words[0]) {
                // If the same word has to be sent we add a blank word
                bufferLength += settings.wordLength;
            }
            bufferLength += settings.wordLength;
            lastWord = words[0];
            if (lastWord == words[1]) {
                // If the same word has to be sent we add a blank word
                bufferLength += settings.wordLength;
            }
            bufferLength += settings.wordLength;
            lastWord = words[1];
        }
        return bufferLength + settings.wordLength;
    }

    /**
     * Converts each byte into two words (hexa) then write the signal in the out array.
     *
     * @param source       Source data
     * @param sourceIndex  First location to read in source data
     * @param sourceLength Read this size from source data
     * @param out          Out array. The size must be greater than outIndex + ((sourceLength + 1) * 2 * settings.wordLength)
     * @param outIndex     Write words signal onto out beginning with this position
     * @param toneRms      Power of words signal output
     * @throws IllegalArgumentException If array size does not fit with parameters
     */
    public void wordsToSignal(byte[] source, int sourceIndex, int sourceLength, short[] out, int outIndex, short toneRms) throws IllegalArgumentException {
        if (sourceIndex + sourceLength > source.length) {
            throw new IllegalArgumentException("Source buffer length is " + source.length + " but request " + (sourceIndex + sourceLength - 1));
        }
        if (outIndex + getSignalLength(source, sourceIndex, sourceLength) > out.length) {
            throw new IllegalArgumentException("Output buffer length is too short" + out.length);
        }
        int lastWord = 0;
        for (int idByte = sourceIndex; idByte < sourceIndex + sourceLength; idByte++) {
            int[] words = byteToWordsIndex(source[idByte]);
            if (idByte > 0 && lastWord == words[0]) {
                // If the same word has to be sent we add a blank word before
                for (int i = outIndex; i < outIndex + settings.wordLength; i++) {
                    out[i] = 0;
                }
                outIndex += settings.wordLength;
            }
            copyTone(words[0], out, outIndex, toneRms);
            outIndex += settings.wordLength;
            lastWord = words[0];
            if (lastWord == words[1]) {
                // If the same word has to be sent we add a blank word before
                for (int i = outIndex; i < outIndex + settings.wordLength; i++) {
                    out[i] = 0;
                }
                outIndex += settings.wordLength;
            }
            copyTone(words[1], out, outIndex, toneRms);
            outIndex += settings.wordLength;
            lastWord = words[1];
        }
        // Finish with a blank word
        for (int i = outIndex; i < outIndex + settings.wordLength; i++) {
            out[i] = 0;
        }
    }

    /**
     * Convert spectrum to byte.
     * @param source Spectrum
     * @return Byte from spectrum or null if
     */
    public Byte spectrumToWord(float[] source) {
        // Sort values by power
        Integer[] indexes = new Integer[source.length];
        for(int i=0; i < source.length; i++) {
            indexes[i] = i;
        }
        Arrays.sort(indexes, new ArrayIndexComparator(source));
        // If the third highest frequency bands level is inferior than second of SignalToNoiseLevel
        if(source[indexes[indexes.length - 2]] - source[indexes[indexes.length - 3]] > settings.getMinimalSignalToNoiseLevel()) {
            Integer word = settings.getWordFromFrequencyTuple(settings.frequencies[indexes[indexes.length - 1]], settings.frequencies[indexes[indexes.length - 2]]);
            if(word != null && (!word.equals(ignoreDuplicateWord))) {
                ignoreDuplicateWord = word;
                if(lastInputWord != null) {
                    byte val = wordsToByte(lastInputWord, word);
                    lastInputWord = null;
                    return val;
                } else {
                    lastInputWord = word;
                    return null;
                }
            } else {
                return null;
            }
        } else {
            ignoreDuplicateWord = Integer.MAX_VALUE;
            return null;
        }
    }


    private static class ArrayIndexComparator implements Comparator<Integer> {
        float[] source;

        public ArrayIndexComparator(float[] source) {
            this.source = source;
        }

        @Override
        public int compare(Integer left, Integer right) {
            return Float.valueOf(source[left]).compareTo(source[right]);
        }
    }
}
