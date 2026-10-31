package ru.secondmemory;
import android.content.Context;
import com.k2fsa.sherpa.onnx.*;

/** GigaAM-v3 RNN-T: real Russian acoustic inference, not text correction. */
final class NeuralSpeech implements AutoCloseable {
 static final String NAME="GigaAM v3 · русский · офлайн";
 private final OfflineRecognizer recognizer;
 NeuralSpeech(Context c){
  FeatureConfig features=new FeatureConfig();features.setSampleRate(16000);features.setFeatureDim(64);
  OfflineTransducerModelConfig rnnt=new OfflineTransducerModelConfig();rnnt.setEncoder("speech/encoder.int8.onnx");rnnt.setDecoder("speech/decoder.onnx");rnnt.setJoiner("speech/joiner.onnx");
  OfflineModelConfig model=new OfflineModelConfig();model.setTransducer(rnnt);model.setTokens("speech/tokens.txt");model.setModelType("nemo_transducer");model.setProvider("cpu");model.setNumThreads(Math.max(1,Math.min(4,Runtime.getRuntime().availableProcessors()-1)));
  OfflineRecognizerConfig config=new OfflineRecognizerConfig();config.setFeatConfig(features);config.setModelConfig(model);config.setDecodingMethod("greedy_search");recognizer=new OfflineRecognizer(c.getAssets(),config);
 }
 String transcribe(float[] samples){if(samples.length<1600)return "";OfflineStream stream=recognizer.createStream();try{stream.acceptWaveform(samples,16000);recognizer.decode(stream);return recognizer.getResult(stream).getText().trim();}finally{stream.release();}}
 public void close(){recognizer.release();}
 static Vad detector(Context c){SileroVadModelConfig silero=new SileroVadModelConfig();silero.setModel("speech/silero_vad.onnx");silero.setMinSilenceDuration(0.7f);silero.setMinSpeechDuration(0.2f);silero.setMaxSpeechDuration(18f);VadModelConfig config=new VadModelConfig();config.setSileroVadModelConfig(silero);config.setSampleRate(16000);config.setNumThreads(1);return new Vad(c.getAssets(),config);}
 static float[] samples(byte[] pcm,int n){float[] samples=new float[n/2];for(int i=0;i<samples.length;i++)samples[i]=(short)((pcm[2*i]&255)|(pcm[2*i+1]<<8))/32768f;return samples;}
}
