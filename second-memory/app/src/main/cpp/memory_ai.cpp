#include "llama.h"
#include <string>
#include <vector>
#include <atomic>
#include <algorithm>
#include <memory>
#include <stdexcept>
#include <mutex>
#ifdef __ANDROID__
#include <jni.h>
#else
#include <iostream>
#include <fstream>
#endif
static std::atomic<bool> cancelled{false};
static std::once_flag init_flag;
struct Engine {
 llama_model *model=nullptr; llama_context *ctx=nullptr;
 ~Engine(){if(ctx)llama_free(ctx);if(model)llama_model_free(model);}
};
static Engine *open_model(const std::string &path){
 std::call_once(init_flag, []{llama_backend_init();});
 auto e=std::make_unique<Engine>();auto mp=llama_model_default_params();mp.n_gpu_layers=0;
 e->model=llama_model_load_from_file(path.c_str(),mp);if(!e->model)return nullptr;
 auto cp=llama_context_default_params();cp.n_ctx=4096;cp.n_batch=256;cp.n_ubatch=128;cp.n_threads=4;cp.n_threads_batch=4;
 cp.abort_callback=[](void*){return cancelled.load();};
 e->ctx=llama_init_from_model(e->model,cp);if(!e->ctx)return nullptr;return e.release();
}
static std::string run(Engine *e,const std::string &prompt,bool analysis=false){
 if(!e||!e->ctx)throw std::runtime_error("No model");
 cancelled=false;llama_kv_cache_clear(e->ctx);
 const auto *vocab=llama_model_get_vocab(e->model);
 int n=-llama_tokenize(vocab,prompt.data(),prompt.size(),nullptr,0,true,true);
 if(n<=0||n>(analysis?2400:3500))throw std::runtime_error("Prompt too long");
 std::vector<llama_token> tokens(n);
 n=llama_tokenize(vocab,prompt.data(),prompt.size(),tokens.data(),tokens.size(),true,true);
 for(int offset=0;offset<n;offset+=256){if(cancelled)throw std::runtime_error("Cancelled");auto batch=llama_batch_get_one(tokens.data()+offset,std::min(256,n-offset));if(llama_decode(e->ctx,batch))throw std::runtime_error("Decode failed");}
 std::unique_ptr<llama_sampler,decltype(&llama_sampler_free)> sampler(llama_sampler_chain_init(llama_sampler_chain_default_params()),llama_sampler_free);
 const char *grammar=R"GRAM(root ::= "{" ws "\"task\":" ws ("false" ws "}" | "true" ws "," ws "\"action\":" ws str ws "," ws "\"assignee\":" ws str ws "," ws "\"due\":" ws str ws "}")
str ::= "\"" ([^"\\\x00-\x1F] | "\\" ["\\/bfnrt])* "\""
ws ::= [ \t\n\r]*
)GRAM";
 const char *analysis_grammar=R"GRAM(root ::= "{" ws "\"items\":" ws "[" ws (item (ws "," ws item)? (ws "," ws item)? (ws "," ws item)? (ws "," ws item)? (ws "," ws item)?)? ws "]" ws "}"
item ::= "{" ws "\"kind\":" ws kind ws "," ws "\"text\":" ws str ws "," ws "\"owner\":" ws str ws "," ws "\"line\":" ws num ws "}"
kind ::= "\"topic\"" | "\"fact\"" | "\"decision\"" | "\"promise\"" | "\"task\"" | "\"question\"" | "\"risk\""
num ::= [1-9] [0-9]*
str ::= "\"" ([^"\\\x00-\x1F] | "\\" ["\\/bfnrt])* "\""
ws ::= [ \t\n\r]*
)GRAM";
 auto *constraint=llama_sampler_init_grammar(vocab,analysis?analysis_grammar:grammar,"root");if(!constraint)throw std::runtime_error("Invalid grammar");
 llama_sampler_chain_add(sampler.get(),constraint);llama_sampler_chain_add(sampler.get(),llama_sampler_init_greedy());
 std::string result;
 for(int i=0;i<(analysis?1536:384);i++){
  if(cancelled)throw std::runtime_error("Cancelled");
  llama_token t=llama_sampler_sample(sampler.get(),e->ctx,-1);if(llama_vocab_is_eog(vocab,t))return result;
  char buf[512];int size=llama_token_to_piece(vocab,t,buf,sizeof(buf),0,true);
  if(size<0)throw std::runtime_error("Token too large");result.append(buf,size);
  auto batch=llama_batch_get_one(&t,1);if(llama_decode(e->ctx,batch))throw std::runtime_error("Decode failed");
 }
 throw std::runtime_error("Output truncated");
}
#ifdef __ANDROID__
static std::string bytes(JNIEnv *env,jbyteArray a){jsize n=env->GetArrayLength(a);std::string s(n,'\0');env->GetByteArrayRegion(a,0,n,reinterpret_cast<jbyte*>(&s[0]));return s;}
extern "C" JNIEXPORT jlong JNICALL Java_ru_secondmemory_LocalAi_open(JNIEnv* env,jclass,jbyteArray path){try{cancelled=false;return reinterpret_cast<jlong>(open_model(bytes(env,path)));}catch(...){return 0;}}
extern "C" JNIEXPORT jbyteArray JNICALL Java_ru_secondmemory_LocalAi_generate(JNIEnv* env,jclass,jlong ptr,jbyteArray prompt){try{auto s=run(reinterpret_cast<Engine*>(ptr),bytes(env,prompt));auto b=env->NewByteArray(s.size());if(b)env->SetByteArrayRegion(b,0,s.size(),reinterpret_cast<const jbyte*>(s.data()));return b;}catch(...){return nullptr;}}
extern "C" JNIEXPORT jbyteArray JNICALL Java_ru_secondmemory_LocalAi_generateAnalysis(JNIEnv* env,jclass,jlong ptr,jbyteArray prompt){try{auto s=run(reinterpret_cast<Engine*>(ptr),bytes(env,prompt),true);auto b=env->NewByteArray(s.size());if(b)env->SetByteArrayRegion(b,0,s.size(),reinterpret_cast<const jbyte*>(s.data()));return b;}catch(...){return nullptr;}}
extern "C" JNIEXPORT void JNICALL Java_ru_secondmemory_LocalAi_free(JNIEnv*,jclass,jlong ptr){delete reinterpret_cast<Engine*>(ptr);}
extern "C" JNIEXPORT void JNICALL Java_ru_secondmemory_LocalAi_cancel(JNIEnv*,jclass){cancelled=true;}
#else
int main(int argc,char**argv){if(argc!=3&&argc!=4)return 2;try{std::unique_ptr<Engine> e(open_model(argv[1]));std::ifstream in(argv[2]);std::string prompt((std::istreambuf_iterator<char>(in)),{});std::cout<<run(e.get(),prompt,argc==4&&std::string(argv[3])=="--analysis")<<std::endl;return 0;}catch(const std::exception &e){std::cerr<<e.what()<<std::endl;return 1;}}
#endif
