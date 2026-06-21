package com.example.myapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RecentTransactionAdapter(
    private var transactions: List<TransactionEntity>
) : RecyclerView.Adapter<RecentTransactionAdapter.TxnViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TxnViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recent_transaction, parent, false)
        return TxnViewHolder(view)
    }

    override fun onBindViewHolder(holder: TxnViewHolder, position: Int) {
        val txn = transactions[position]
        holder.tvTxnDate.text = txn.date
        val amount =
            if (txn.received != 0.0) "+₹%.2f".format(txn.received)
            else "-₹%.2f".format(txn.given)
        holder.tvTxnAmount.text = amount
        if (txn.received != 0.0)
            holder.tvTxnAmount.setTextColor(holder.itemView.resources.getColor(android.R.color.holo_green_dark))
        else
            holder.tvTxnAmount.setTextColor(holder.itemView.resources.getColor(android.R.color.holo_red_dark))
    }

    override fun getItemCount(): Int = transactions.size

    fun update(list: List<TransactionEntity>) {
        transactions = list
        notifyDataSetChanged()
    }

    class TxnViewHolder(view: View): RecyclerView.ViewHolder(view) {
        val tvTxnDate: TextView = view.findViewById(R.id.tvTxnDate)
        val tvTxnAmount: TextView = view.findViewById(R.id.tvTxnAmount)
    }
}