#python run_word2vec.py -d

from gensim import models
from flask import request
from flask import Response
from flask import Flask
app = Flask(__name__)

@app.route("/", methods=['GET','POST'])
def getSimilarityScore():
    if request.method == 'POST':
        req_json = request.get_json()
    
        model = models.Word2Vec.load_word2vec_format('AppTestMigrator/resources/word2vec_model.bin', binary=True)
        try:
            similarity_score = model.similarity(req_json['vocab1'], req_json['vocab2'])
        except:
    	    similarity_score = 0
    	    
        resp = Response(repr(similarity_score), status=201, mimetype='application/json')
        return resp
    else:
        return "The request is not valid."
    
if __name__ == "__main__":
    app.run(debug=True)
